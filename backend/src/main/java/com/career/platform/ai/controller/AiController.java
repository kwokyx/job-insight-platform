package com.career.platform.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.ai.client.LlmClient;
import com.career.platform.ai.entity.AiConversation;
import com.career.platform.ai.entity.AiMessage;
import com.career.platform.ai.mapper.AiConversationMapper;
import com.career.platform.ai.mapper.AiMessageMapper;
import com.career.platform.ai.service.AiAgentService;
import com.career.platform.ai.service.AiFileImportService;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.UserInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Validated
@Tag(name = "AI Assistant", description = "Chat, agent tools, and profile import")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private static final String SYSTEM_PROMPT =
            "You are the built-in assistant for a career analytics platform. "
                    + "Focus on jobs, salary, skills, reports, and career planning. "
                    + "Answer directly to the end user in concise, practical language. "
                    + "Do not reveal internal reasoning, step-by-step analysis, hidden chain-of-thought, or meta commentary such as "
                    + "\"the user asks\", \"I need to\", \"let me think\", or \"based on the prompt\". "
                    + "Do not describe what you are going to do. Just provide the final helpful answer. "
                    + "If the user asks something unrelated, redirect them back to the platform scope.";

    private static final Map<String, Pattern> INTENT_PATTERNS = new HashMap<>();

    static {
        INTENT_PATTERNS.put("city", Pattern.compile("(beijing|shanghai|guangzhou|shenzhen|hangzhou|chengdu|\\u5317\\u4eac|\\u4e0a\\u6d77|\\u5e7f\\u5dde|\\u6df1\\u5733|\\u676d\\u5dde|\\u6210\\u90fd)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("salary", Pattern.compile("(salary|pay|compensation|\\u85aa\\u8d44|\\u5de5\\u8d44|\\u85aa\\u916c)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("skill", Pattern.compile("(python|java|javascript|typescript|go|rust|vue|react|spring|node|tensorflow|pytorch|sql|docker|kubernetes|\\u6280\\u80fd|\\u80fd\\u529b)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("education", Pattern.compile("(education|bachelor|master|phd|\\u5b66\\u5386|\\u672c\\u79d1|\\u7855\\u58eb|\\u535a\\u58eb)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("industry", Pattern.compile("(internet|finance|education|medical|ecommerce|game|software|ai|\\u4e92\\u8054\\u7f51|\\u91d1\\u878d|\\u6559\\u80b2|\\u533b\\u7597|\\u7535\\u5546|\\u6e38\\u620f|\\u8f6f\\u4ef6|\\u4eba\\u5de5\\u667a\\u80fd)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("career", Pattern.compile("(career|plan|growth|interview|job|\\u804c\\u4e1a|\\u89c4\\u5212|\\u9762\\u8bd5|\\u5c97\\u4f4d|\\u6c42\\u804c)", Pattern.CASE_INSENSITIVE));
    }

    private final LlmClient llmClient;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final JobPostingMapper jobMapper;
    private final StringRedisTemplate redisTemplate;
    private final AiAgentService aiAgentService;
    private final AiFileImportService aiFileImportService;
    private final ObjectMapper objectMapper;
    private final UserInsightService userInsightService;

    @Value("${career.ai.daily-quota}")
    private int dailyQuota;

    @Data
    public static class ChatRequest {
        @NotBlank(message = "message cannot be empty")
        private String message;
        private String sessionId;
    }

    @Data
    public static class AgentQueryRequest {
        @NotBlank(message = "message cannot be empty")
        private String message;
        private String tool;
    }

    @Operation(summary = "AI chat stream")
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@Valid @RequestBody ChatRequest req) {
        Long userId = getCurrentUserId();
        checkQuota(userId);

        SseEmitter emitter = new SseEmitter(300_000L);
        new Thread(() -> handleChatStream(userId, req, emitter)).start();
        return emitter;
    }

    @Operation(summary = "Run agent query")
    @PostMapping("/agent/query")
    public R<?> runAgentQuery(@Valid @RequestBody AgentQueryRequest req) {
        Long userId = getCurrentUserId();
        checkQuota(userId);
        Map<String, Object> result = aiAgentService.runAgent(userId, req.getMessage(), req.getTool());
        incrementQuota(userId);
        return R.ok(result);
    }

    @Operation(summary = "Import profile from uploaded file")
    @PostMapping("/agent/import-profile")
    public R<?> importProfile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "overwriteSkills", defaultValue = "false") boolean overwriteSkills
    ) {
        Long userId = getCurrentUserId();
        String text = aiFileImportService.extractText(file);
        Map<String, Object> result = aiAgentService.importProfileFromText(
                userId,
                file.getOriginalFilename(),
                text,
                overwriteSkills
        );
        return R.ok(result);
    }

    @Operation(summary = "List AI conversations")
    @GetMapping("/conversations")
    public R<?> listConversations() {
        Long userId = getCurrentUserId();
        List<AiConversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getStatus, 1)
                        .orderByDesc(AiConversation::getUpdatedAt)
        );
        return R.ok(list);
    }

    @Operation(summary = "Get a conversation")
    @GetMapping("/conversations/{sessionId}")
    public R<?> getConversation(@PathVariable String sessionId) {
        Long userId = getCurrentUserId();
        AiConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId)
                        .eq(AiConversation::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (conv == null) {
            throw BusinessException.notFound("Conversation not found");
        }

        List<AiMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conv.getId())
                        .orderByAsc(AiMessage::getCreatedAt)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("conversation", conv);
        result.put("messages", messages);
        return R.ok(result);
    }

    @Log("Delete AI conversation")
    @Operation(summary = "Archive conversation")
    @DeleteMapping("/conversations/{sessionId}")
    public R<?> deleteConversation(@PathVariable String sessionId) {
        Long userId = getCurrentUserId();
        AiConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId)
                        .eq(AiConversation::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (conv == null) {
            throw BusinessException.notFound("Conversation not found");
        }

        conv.setStatus(0);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return R.ok("Conversation deleted");
    }

    @Operation(summary = "Get AI quota")
    @GetMapping("/quota")
    public R<?> getQuota() {
        Long userId = getCurrentUserId();
        String key = quotaKey(userId);
        int used = getQuotaUsed(key);

        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("used", used);
        quota.put("limit", dailyQuota);
        quota.put("remaining", Math.max(0, dailyQuota - used));
        return R.ok(quota);
    }

    private void handleChatStream(Long userId, ChatRequest req, SseEmitter emitter) {
        try {
            AiConversation conversation = getOrCreateConversation(userId, req.getSessionId());
            saveMessage(conversation.getId(), "user", req.getMessage());

            List<Map<String, String>> history = loadHistory(conversation.getId(), 10);
            String prompt = buildEnhancedPrompt(userId, req.getMessage());

            if (!StringUtils.hasText(conversation.getContextType()) || "general".equals(conversation.getContextType())) {
                conversation.setContextType(detectIntent(req.getMessage()));
            }

            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", conversation.getSessionId());
            emitter.send(SseEmitter.event().name("session").data(sessionData));

            StringBuilder fullResponse = new StringBuilder();
            StringBuilder pendingResponse = new StringBuilder();
            long startTime = System.currentTimeMillis();
            AtomicBoolean inThinking = new AtomicBoolean(false);
            AtomicBoolean emitterCompleted = new AtomicBoolean(false);
            AtomicBoolean answerStarted = new AtomicBoolean(false);
            emitter.onCompletion(() -> emitterCompleted.set(true));
            emitter.onTimeout(() -> emitterCompleted.set(true));

            Flux<String> stream = llmClient.chatStream(prompt, history);
            stream.doOnNext(tokenJson -> {
                        try {
                            if (emitterCompleted.get()) {
                                return;
                            }
                            JsonNode delta = objectMapper.readTree(tokenJson);
                            String content = delta.path("content").asText("");
                            String streamText = content;
                            String visibleText = stripThinkingContent(streamText, inThinking);
                            if (!StringUtils.hasText(visibleText)) {
                                return;
                            }
                            String deliverable = extractDeliverableText(visibleText, pendingResponse, answerStarted);
                            if (!StringUtils.hasText(deliverable)) {
                                return;
                            }
                            fullResponse.append(deliverable);

                            Map<String, Object> msgData = new HashMap<>();
                            msgData.put("content", deliverable);
                            msgData.put("reasoning_content", "");
                            emitter.send(SseEmitter.event().name("message").data(msgData));
                        } catch (IllegalStateException ignore) {
                            emitterCompleted.set(true);
                        } catch (Exception e) {
                            log.warn("Failed to process stream token: {}", e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            if (!StringUtils.hasText(fullResponse.toString())) {
                                String fallback = sanitizeAssistantText(pendingResponse.toString());
                                if (!StringUtils.hasText(fallback)) {
                                    fallback = sanitizeAssistantText(llmClient.chat(prompt, history));
                                }
                                if (!StringUtils.hasText(fallback)) {
                                    fallback = buildLocalFallbackReply(userId, req.getMessage());
                                }
                                fullResponse.append(fallback);
                                Map<String, Object> fallbackMsg = new HashMap<>();
                                fallbackMsg.put("content", fallback);
                                fallbackMsg.put("reasoning_content", "");
                                emitter.send(SseEmitter.event().name("message").data(fallbackMsg));
                            }

                            long latency = System.currentTimeMillis() - startTime;
                            saveAssistantMessage(conversation, req.getMessage(), fullResponse.toString(), latency);
                            incrementQuota(userId);

                            Map<String, Object> doneData = new HashMap<>();
                            doneData.put("latencyMs", latency);
                            if (emitterCompleted.compareAndSet(false, true)) {
                                emitter.send(SseEmitter.event().name("done").data(doneData));
                                emitter.complete();
                            }
                        } catch (IllegalStateException e) {
                            // Client closed connection or emitter already completed; no-op.
                        } catch (Exception e) {
                            log.error("Failed to finish stream", e);
                            if (emitterCompleted.compareAndSet(false, true)) {
                                emitter.completeWithError(e);
                            }
                        }
                    })
                    .doOnError(error -> {
                        log.error("LLM stream failed", error);
                        try {
                            String fallback = sanitizeAssistantText(buildLocalFallbackReply(userId, req.getMessage()));
                            fullResponse.append(fallback);

                            Map<String, Object> msgData = new HashMap<>();
                            msgData.put("content", fallback);
                            msgData.put("reasoning_content", "");
                            emitter.send(SseEmitter.event().name("message").data(msgData));

                            saveAssistantMessage(conversation, req.getMessage(), fullResponse.toString(), System.currentTimeMillis() - startTime);
                            incrementQuota(userId);
                            if (emitterCompleted.compareAndSet(false, true)) {
                                emitter.send(SseEmitter.event().name("done").data(Collections.singletonMap("latencyMs", System.currentTimeMillis() - startTime)));
                                emitter.complete();
                            }
                        } catch (IllegalStateException ignored) {
                        } catch (IOException ignored) {
                            if (emitterCompleted.compareAndSet(false, true)) {
                                emitter.completeWithError(error);
                            }
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("AI chat request failed", e);
            try {
                Map<String, Object> errData = new HashMap<>();
                errData.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errData));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    private void saveAssistantMessage(AiConversation conversation, String userMessage, String content, long latency) {
        AiMessage assistantMsg = new AiMessage();
        assistantMsg.setConversationId(conversation.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(content);
        assistantMsg.setContentType("text");
        assistantMsg.setLatencyMs((int) latency);
        assistantMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMsg);

        conversation.setMessageCount((conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 2);
        conversation.setUpdatedAt(LocalDateTime.now());
        if (!StringUtils.hasText(conversation.getTitle())) {
            conversation.setTitle(userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage);
        }
        conversationMapper.updateById(conversation);
    }

    private AiConversation getOrCreateConversation(Long userId, String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            AiConversation existing = conversationMapper.selectOne(
                    new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getSessionId, sessionId)
                            .eq(AiConversation::getUserId, userId)
                            .last("LIMIT 1")
            );
            if (existing != null) {
                return existing;
            }
        }

        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        conv.setContextType("general");
        conv.setStatus(1);
        conv.setMessageCount(0);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    private void saveMessage(Long conversationId, String role, String content) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setContentType("text");
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private List<Map<String, String>> loadHistory(Long conversationId, int limit) {
        List<AiMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByDesc(AiMessage::getCreatedAt)
                        .last("LIMIT " + limit)
        );
        Collections.reverse(msgs);
        return msgs.stream()
                .map(message -> {
                    Map<String, String> item = new HashMap<>();
                    item.put("role", message.getRole());
                    item.put("content", message.getContent());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private String detectIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return "general";
        }
        if (INTENT_PATTERNS.get("salary").matcher(message).find()) {
            return "salary_analysis";
        }
        if (INTENT_PATTERNS.get("career").matcher(message).find()) {
            return "career_advice";
        }
        if (INTENT_PATTERNS.get("skill").matcher(message).find()) {
            return "skill_analysis";
        }
        if (INTENT_PATTERNS.get("industry").matcher(message).find()) {
            return "industry_analysis";
        }
        if (INTENT_PATTERNS.get("city").matcher(message).find()) {
            return "city_analysis";
        }
        if (INTENT_PATTERNS.get("education").matcher(message).find()) {
            return "education_analysis";
        }
        return "general";
    }

    private String buildEnhancedPrompt(Long userId, String userMessage) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);

        try {
            Map<String, Object> stats = jobMapper.overviewStats();
            long totalJobs = jobMapper.selectCount(null);
            sb.append("\n\nPlatform data overview:");
            sb.append("\n- Total jobs: ").append(totalJobs);
            if (stats.get("avgSalaryMin") != null) {
                sb.append("\n- Average salary range: ")
                        .append(stats.get("avgSalaryMin"))
                        .append("K - ")
                        .append(stats.get("avgSalaryMax"))
                        .append("K");
            }

            if (INTENT_PATTERNS.get("skill").matcher(userMessage).find() || INTENT_PATTERNS.get("career").matcher(userMessage).find()) {
                sb.append("\n- Top skills: ").append(simpleJson(jobMapper.topSkills(8)));
            }
            if (INTENT_PATTERNS.get("city").matcher(userMessage).find()) {
                sb.append("\n- Top cities: ").append(simpleJson(jobMapper.aggregateByCity(8)));
            }
            if (INTENT_PATTERNS.get("salary").matcher(userMessage).find()) {
                sb.append("\n- Salary by education: ").append(simpleJson(jobMapper.aggregateByEducation()));
                sb.append("\n- Salary by experience: ").append(simpleJson(jobMapper.aggregateByExperience()));
            }
            if (INTENT_PATTERNS.get("industry").matcher(userMessage).find()) {
                sb.append("\n- Top industries: ").append(simpleJson(jobMapper.aggregateByIndustry(8)));
            }
            sb.append("\n- ").append(userInsightService.buildPromptContext(userId));
        } catch (Exception e) {
            log.warn("Failed to enrich prompt with platform stats: {}", e.getMessage());
        }

        return sb.toString();
    }

    private String stripThinkingContent(String chunk, AtomicBoolean inThinking) {
        if (!StringUtils.hasText(chunk)) {
            return "";
        }
        String text = chunk;
        StringBuilder out = new StringBuilder();
        int idx = 0;

        while (idx < text.length()) {
            if (inThinking.get()) {
                int end = text.indexOf("</think>", idx);
                if (end < 0) {
                    return out.toString();
                }
                inThinking.set(false);
                idx = end + "</think>".length();
                continue;
            }

            int start = text.indexOf("<think>", idx);
            if (start < 0) {
                out.append(text.substring(idx));
                break;
            }

            out.append(text, idx, start);
            int nextIdx = start + "<think>".length();
            int end = text.indexOf("</think>", nextIdx);
            if (end < 0) {
                inThinking.set(true);
                break;
            }
            idx = end + "</think>".length();
        }

        return out.toString().replace("</think>", "");
    }

    private String sanitizeAssistantText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String sanitized = stripThinkingContent(text, new AtomicBoolean(false)).trim();
        sanitized = sanitized.replaceAll("(?is)^(okay|ok|alright|sure)[,\\s]+", "");
        sanitized = sanitized.replaceAll("(?is)^it seems like your message might be unclear.*?career planning!\\s*", "");
        sanitized = extractFinalUserFacingAnswer(sanitized);
        sanitized = removeMetaPreamble(sanitized).trim();
        if (looksLikeMetaPreamble(sanitized) && sanitized.contains("\n\n")) {
            String[] parts = sanitized.split("\\r?\\n\\r?\\n");
            sanitized = parts[parts.length - 1].trim();
        }
        return sanitized;
    }

    private String extractDeliverableText(String chunk, StringBuilder pendingResponse, AtomicBoolean answerStarted) {
        if (!StringUtils.hasText(chunk)) {
            return "";
        }
        if (answerStarted.get()) {
            return chunk;
        }
        pendingResponse.append(chunk);
        String candidate = pendingResponse.toString();

        if (candidate.length() < 180 && !candidate.contains("\n\n")) {
            return "";
        }

        if (looksLikeMetaPreamble(candidate)) {
            int markerIndex = findAnswerMarkerIndex(candidate);
            if (markerIndex >= 0) {
                answerStarted.set(true);
                String deliverable = candidate.substring(markerIndex);
                pendingResponse.setLength(0);
                return deliverable;
            }
            return "";
        }

        if (candidate.length() >= 32 || candidate.contains("\n")) {
            answerStarted.set(true);
            pendingResponse.setLength(0);
            return candidate;
        }
        return "";
    }

    private boolean looksLikeMetaPreamble(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        return normalized.startsWith("okay")
                || normalized.startsWith("ok")
                || normalized.startsWith("alright")
                || normalized.startsWith("sure")
                || normalized.startsWith("so the user")
                || normalized.startsWith("the user")
                || normalized.startsWith("i need")
                || normalized.startsWith("first, i")
                || normalized.contains("the user")
                || normalized.contains("i need to")
                || normalized.contains("i should")
                || normalized.contains("let me")
                || normalized.contains("looking at")
                || normalized.contains("first,")
                || normalized.contains("okay, so")
                || normalized.contains("the message is")
                || normalized.contains("i can")
                || normalized.contains("i'll")
                || normalized.contains("i will");
    }

    private int findAnswerMarkerIndex(String text) {
        String[] markers = {
                "\n1.", "\n- ", "\n###", "结论", "建议", "行动", "分析", "回答如下", "最终建议", "重点如下",
                "Here are", "Based on", "You can", "I recommend", "To improve", "Final answer"
        };
        int best = -1;
        for (String marker : markers) {
            int idx = text.indexOf(marker);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private String removeMetaPreamble(String text) {
        int markerIndex = findAnswerMarkerIndex(text);
        if (markerIndex > 0) {
            String prefix = text.substring(0, markerIndex).toLowerCase();
            if (looksLikeMetaPreamble(prefix)) {
                return text.substring(markerIndex).trim();
            }
        }
        if (looksLikeMetaPreamble(text) && text.contains("\n\n")) {
            String[] parts = text.split("\\r?\\n\\r?\\n");
            return parts[parts.length - 1].trim();
        }
        return text;
    }

    private String extractFinalUserFacingAnswer(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] answerMarkers = {
                "It seems like",
                "Could you please",
                "Please provide",
                "Are you looking",
                "Here are",
                "Based on",
                "You can",
                "I recommend",
                "\n1.",
                "1. "
        };
        int best = -1;
        for (String marker : answerMarkers) {
            int idx = text.lastIndexOf(marker);
            if (idx >= 0 && idx > best) {
                best = idx;
            }
        }
        if (best > 0) {
            String prefix = text.substring(0, best);
            if (looksLikeMetaPreamble(prefix)) {
                return text.substring(best).trim();
            }
        }
        return text;
    }

    private String buildLocalFallbackReply(Long userId, String userMessage) {
        try {
            Map<String, Object> agentResult = aiAgentService.runAgent(userId, userMessage, null);
            String agentAnswer = String.valueOf(agentResult.getOrDefault("answer", ""));
            if (StringUtils.hasText(agentAnswer)) {
                return agentAnswer;
            }
        } catch (Exception e) {
            log.warn("Agent fallback failed, continue with advisory fallback: {}", e.getMessage());
        }

        Map<String, Object> advisory = userInsightService.buildPlatformAdvisory(userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> market = (Map<String, Object>) advisory.getOrDefault("marketOverview", Collections.emptyMap());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) advisory.getOrDefault("actions", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) advisory.getOrDefault("missingSkills", Collections.emptyList());

        StringBuilder answer = new StringBuilder();
        answer.append("AI 服务未返回有效内容，已切换为平台数据兜底分析。\n\n");
        answer.append("问题焦点：").append(userMessage).append("\n");
        answer.append("市场概况：平均薪资 ")
                .append(market.getOrDefault("avgSalaryMin", "N/A"))
                .append("K - ")
                .append(market.getOrDefault("avgSalaryMax", "N/A"))
                .append("K，岗位总量 ")
                .append(market.getOrDefault("totalJobs", "N/A"))
                .append("。\n");
        answer.append("画像完整度：").append(advisory.getOrDefault("profileCompletenessScore", 0)).append("%，");
        answer.append("市场匹配度：").append(advisory.getOrDefault("marketAlignmentScore", 0)).append("%。\n");

        if (!gaps.isEmpty()) {
            answer.append("优先补齐技能：");
            for (int i = 0; i < Math.min(3, gaps.size()); i++) {
                if (i > 0) {
                    answer.append("、");
                }
                answer.append(gaps.get(i).get("skill"));
            }
            answer.append("。\n");
        }

        if (!actions.isEmpty()) {
            answer.append("下一步建议：\n");
            for (int i = 0; i < Math.min(3, actions.size()); i++) {
                Map<String, Object> action = actions.get(i);
                answer.append(i + 1)
                        .append(". ")
                        .append(action.getOrDefault("title", "行动项"))
                        .append("：")
                        .append(action.getOrDefault("detail", ""))
                        .append("\n");
            }
        }
        return answer.toString();
    }

    private String simpleJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void checkQuota(Long userId) {
        String key = quotaKey(userId);
        int used = getQuotaUsed(key);
        if (used >= dailyQuota) {
            throw BusinessException.of(429, "AI daily quota exceeded");
        }
    }

    private void incrementQuota(Long userId) {
        String key = quotaKey(userId);
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 25, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis unavailable, skip quota increment: {}", e.getMessage());
        }
    }

    private int getQuotaUsed(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return 0;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Redis unavailable, fallback AI quota used=0 for {}: {}", key, e.getMessage());
            return 0;
        }
    }

    private String quotaKey(Long userId) {
        return "ai:quota:" + userId + ":" + LocalDate.now();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Please login first");
        }
        if (auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        throw BusinessException.unauthorized("Invalid login state");
    }
}
