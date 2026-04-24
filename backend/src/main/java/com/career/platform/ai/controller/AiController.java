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
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.ReadinessService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.system.entity.SysUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Validated
@Tag(name = "AI Assistant", description = "Chat, agent tools, and profile import")
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiController.class);

    private static final String SYSTEM_PROMPT = "你是职业能力大数据服务平台的 AI 助手，回答一律使用中文。\n"
            + "\n"
            + "长度控制：\n"
            + "- 问候 / 闲聊 / 简单澄清：1-2 句话，不要给列表也不要给标题。\n"
            + "- 普通数据或咨询问题：150-250 字，2-4 个要点即可。\n"
            + "- 只有用户明确要求\"详细\"\"展开\"\"计划\"时才写长文。\n"
            + "\n"
            + "Markdown 规范（严格遵守，否则前端渲染会坏）：\n"
            + "- 小节标题用 `### 标题`，`###` 后必须有一个空格，标题独占一行，标题前后各留一个空行。\n"
            + "- 列表项前留空行；`- ` 之后必须有空格；嵌套列表用两个空格缩进。\n"
            + "- 加粗写法是 `**文本**`：两个 `**` 紧贴文本，**内部不能有空格**。正确：`**Java**`；错误：`** Java **`（这会渲染成字面星号）。\n"
            + "- 如果加粗后面紧跟中文，在闭合 `**` 之外插一个半角空格，再写中文。正确：`**Java** 是主流`；错误：`**Java**是主流`。\n"
            + "- 不要把标题、加粗和正文挤在同一行；一段话结束后要有换行。\n"
            + "- 必要时用表格（| ... | ... |）对比数据，不要滥用。\n"
            + "\n"
            + "内容原则：\n"
            + "- 直接给出判断和建议，**禁止使用**\"结论：\"\"答案：\"\"分析：\"这类标签式前缀。\n"
            + "- 先说要点，再补数据/理由，最后给行动建议。\n"
            + "- 禁止元评论（例如\"我会帮你分析\"\"好的，我来\"\"希望这对你有帮助\"）。\n"
            + "- 禁止复述系统提供的平台原始数据，消化后再输出。";

    private static final Map<String, Pattern> INTENT_PATTERNS = new HashMap<>();
    private static final Map<Integer, Set<String>> ROLE_ALLOWED_TOOLS = new HashMap<>();

    static {
        INTENT_PATTERNS.put("city", Pattern.compile(
                "(beijing|shanghai|guangzhou|shenzhen|hangzhou|chengdu|\\u5317\\u4eac|\\u4e0a\\u6d77|\\u5e7f\\u5dde|\\u6df1\\u5733|\\u676d\\u5dde|\\u6210\\u90fd)",
                Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("salary", Pattern.compile(
                "(salary|pay|compensation|\\u85aa\\u8d44|\\u5de5\\u8d44|\\u85aa\\u916c)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("skill", Pattern.compile(
                "(python|java|javascript|typescript|go|rust|vue|react|spring|node|tensorflow|pytorch|sql|docker|kubernetes|\\u6280\\u80fd|\\u80fd\\u529b)",
                Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("education",
                Pattern.compile(
                        "(education|bachelor|master|phd|\\u5b66\\u5386|\\u672c\\u79d1|\\u7855\\u58eb|\\u535a\\u58eb)",
                        Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("industry", Pattern.compile(
                "(internet|finance|education|medical|ecommerce|game|software|ai|\\u4e92\\u8054\\u7f51|\\u91d1\\u878d|\\u6559\\u80b2|\\u533b\\u7597|\\u7535\\u5546|\\u6e38\\u620f|\\u8f6f\\u4ef6|\\u4eba\\u5de5\\u667a\\u80fd)",
                Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("career", Pattern.compile(
                "(career|plan|growth|interview|job|\\u804c\\u4e1a|\\u89c4\\u5212|\\u9762\\u8bd5|\\u5c97\\u4f4d|\\u6c42\\u804c)",
                Pattern.CASE_INSENSITIVE));

        ROLE_ALLOWED_TOOLS.put(SysUser.ROLE_USER, allowedTools(
                "auto", "market_overview", "profile_snapshot", "salary_insight", "skill_gap", "job_match", "career_path"
        ));
        ROLE_ALLOWED_TOOLS.put(SysUser.ROLE_TEACHER, allowedTools(
                "auto", "course_supply_demand", "teaching_reform", "market_overview"
        ));
        ROLE_ALLOWED_TOOLS.put(SysUser.ROLE_ADMIN, allowedTools(
                "auto", "user_governance", "operations_dashboard", "market_overview"
        ));
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
    private final Executor aiChatExecutor;
    private final ReadinessService readinessService;

    @Value("${career.ai.daily-quota}")
    private int dailyQuota;

    public AiController(LlmClient llmClient, AiConversationMapper conversationMapper, 
                        AiMessageMapper messageMapper, JobPostingMapper jobMapper, 
                        StringRedisTemplate redisTemplate, AiAgentService aiAgentService, 
                        AiFileImportService aiFileImportService, ObjectMapper objectMapper, 
                        UserInsightService userInsightService, 
                        ReadinessService readinessService,
                        @Qualifier("aiChatExecutor") Executor aiChatExecutor) {
        this.llmClient = llmClient;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.jobMapper = jobMapper;
        this.redisTemplate = redisTemplate;
        this.aiAgentService = aiAgentService;
        this.aiFileImportService = aiFileImportService;
        this.objectMapper = objectMapper;
        this.userInsightService = userInsightService;
        this.readinessService = readinessService;
        this.aiChatExecutor = aiChatExecutor;
    }

    public static class ChatRequest {
        @NotBlank(message = "message cannot be empty")
        private String message;
        private String sessionId;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    public static class AgentQueryRequest {
        @NotBlank(message = "message cannot be empty")
        private String message;
        private String tool;
        private String sessionId;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getTool() { return tool; }
        public void setTool(String tool) { this.tool = tool; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }


    @Log("AI chat stream")
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@Valid @RequestBody ChatRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        checkQuota(userId);

        SseEmitter emitter = new SseEmitter(300_000L);
        // 立即发送 typing 事件，前端即时显示《AI 正在思考》状态
        try {
            emitter.send(SseEmitter.event().name("typing").data(Collections.singletonMap("status", "thinking")));
        } catch (IOException ignored) {
        }
        // 使用池化线程，替代裸 new Thread()
        aiChatExecutor.execute(() -> handleChatStream(userId, req, emitter));
        return emitter;
    }

    @Operation(summary = "Run agent query")
    @PostMapping("/agent/query")
    public R<?> runAgentQuery(@Valid @RequestBody AgentQueryRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer roleType = SecurityUtils.getCurrentRoleType();
        validateAgentToolAccess(roleType, req.getTool());
        validateReadinessForAgent(userId, roleType, req.getTool());
        checkQuota(userId);
        Map<String, Object> result = aiAgentService.runAgent(userId, roleType, req.getMessage(), req.getTool());
        incrementQuota(userId);
        return R.ok(result);
    }

    @Log("AI agent stream")
    @Operation(summary = "Run agent query as SSE stream")
    @PostMapping(value = "/agent/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter agentStream(@Valid @RequestBody AgentQueryRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer roleType = SecurityUtils.getCurrentRoleType();
        validateAgentToolAccess(roleType, req.getTool());
        validateReadinessForAgent(userId, roleType, req.getTool());
        checkQuota(userId);

        SseEmitter emitter = new SseEmitter(300_000L);
        try {
            emitter.send(SseEmitter.event().name("typing").data(Collections.singletonMap("status", "thinking")));
        } catch (IOException ignored) {}

        aiChatExecutor.execute(() -> handleAgentStream(userId, roleType, req, emitter));
        return emitter;
    }

    private void handleAgentStream(Long userId, Integer roleType, AgentQueryRequest req, SseEmitter emitter) {
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));

        long startTime = System.currentTimeMillis();
        final AiConversation conversation;
        try {
            conversation = getOrCreateConversation(userId, req.getSessionId());
            saveMessage(conversation.getId(), "user", req.getMessage());
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", conversation.getSessionId());
            emitter.send(SseEmitter.event().name("session").data(sessionData));
        } catch (Exception e) {
            log.error("Agent stream init failed", e);
            safeSend(emitter, "error", Collections.singletonMap("message", e.getMessage()), completed);
            if (completed.compareAndSet(false, true)) emitter.completeWithError(e);
            return;
        }

        final StringBuilder fullContent = new StringBuilder();
        final List<Map<String, Object>> trace = new ArrayList<>();
        final Map<String, Object>[] finalResultHolder = new Map[]{null};

        try {
            aiAgentService.runAgentStream(userId, roleType, req.getMessage(), req.getTool(),
                    new AiAgentService.AgentStreamListener() {
                        @Override
                        public void onToolCall(String tool, Map<String, Object> args) {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("tool", tool);
                            data.put("args", args);
                            safeSend(emitter, "tool_call", data, completed);
                        }

                        @Override
                        public void onToolResult(String tool, String label, String summary) {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("tool", tool);
                            data.put("label", label);
                            data.put("summary", summary);
                            trace.add(data);
                            safeSend(emitter, "tool_result", data, completed);
                        }

                        @Override
                        public void onContent(String chunk) {
                            if (!StringUtils.hasText(chunk)) return;
                            fullContent.append(chunk);
                            safeSend(emitter, "message", Collections.singletonMap("content", chunk), completed);
                        }

                        @Override
                        public void onFinalResult(Map<String, Object> result) {
                            finalResultHolder[0] = result;
                        }

                        @Override
                        public void onError(String message) {
                            safeSend(emitter, "error", Collections.singletonMap("message", message), completed);
                        }
                    });

            long latency = System.currentTimeMillis() - startTime;
            String answer = fullContent.toString();
            if (finalResultHolder[0] != null) {
                Object ans = finalResultHolder[0].get("answer");
                if (!StringUtils.hasText(answer) && ans != null) {
                    answer = String.valueOf(ans);
                }
            }
            // trace 里带上每次工具调用的 tool/label/summary，前端打开历史会话时可直接复现 chip
            saveAssistantMessage(conversation, req.getMessage(), answer, "", trace.isEmpty() ? null : trace, latency);
            incrementQuota(userId);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("latencyMs", latency);
            if (finalResultHolder[0] != null) {
                done.put("toolPlan", finalResultHolder[0].get("toolPlan"));
                done.put("toolTrace", finalResultHolder[0].get("toolTrace"));
                done.put("toolResult", finalResultHolder[0].get("toolResult"));
                done.put("reasoningSummary", finalResultHolder[0].get("reasoningSummary"));
            }
            safeSend(emitter, "done", done, completed);
            if (completed.compareAndSet(false, true)) emitter.complete();
        } catch (Exception e) {
            log.error("Agent stream failed", e);
            safeSend(emitter, "error", Collections.singletonMap("message", e.getMessage()), completed);
            if (completed.compareAndSet(false, true)) emitter.completeWithError(e);
        }
    }

    private void safeSend(SseEmitter emitter, String name, Object data, AtomicBoolean completed) {
        if (completed.get()) return;
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IllegalStateException | IOException e) {
            // 客户端断开或 emitter 已结束，忽略
        }
    }

    @Operation(summary = "Import profile from uploaded file")
    @PostMapping("/agent/import-profile")
    public R<?> importProfile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "overwriteSkills", defaultValue = "false") boolean overwriteSkills) {
        Long userId = SecurityUtils.getCurrentUserId();
        String text = aiFileImportService.extractText(file);
        Map<String, Object> result = aiAgentService.importProfileFromText(
                userId,
                file.getOriginalFilename(),
                text,
                overwriteSkills);
        // 同时进行结构化解析
        Map<String, Object> structured = aiFileImportService.parseResumeStructured(text);
        if (!structured.isEmpty()) {
            result.put("structuredFields", structured);
        }
        return R.ok(result);
    }

    @Operation(summary = "Parse resume file and return structured fields")
    @PostMapping("/agent/parse-resume")
    public R<?> parseResume(@RequestPart("file") MultipartFile file) {
        String text = aiFileImportService.extractText(file);
        Map<String, Object> structured = aiFileImportService.parseResumeStructured(text);
        structured.put("rawTextLength", text.length());
        return R.ok(structured);
    }

    @Operation(summary = "AI quick command presets")
    @GetMapping("/quick-commands")
    public R<?> quickCommands() {
        List<Map<String, String>> commands = new java.util.ArrayList<>();
        commands.add(quickCmd("skill_gap", "分析我的技能差距", "根据我的个人画像，分析我当前技能与目标岗位的差距，给出学习建议"));
        commands.add(quickCmd("job_match", "推荐适合我的岗位", "根据我的技能和求职偏好，推荐最匹配的岗位并说明原因"));
        commands.add(quickCmd("resume_optimize", "帮我优化简历", "分析我的个人画像信息，给出简历优化建议，包括关键词补充和内容调整"));
        commands.add(quickCmd("career_plan", "制定职业发展计划", "根据我的当前岗位和目标岗位，制定一份3-6个月的职业成长计划"));
        commands.add(quickCmd("market_insight", "当前就业市场分析", "分析当前就业市场的整体趋势，包括热门城市、热门行业和薪资水平"));
        commands.add(quickCmd("interview_prep", "模拟面试准备", "根据我的目标岗位，列出高频面试问题并给出参考答案"));
        return R.ok(commands);
    }

    private Map<String, String> quickCmd(String id, String label, String message) {
        Map<String, String> cmd = new java.util.LinkedHashMap<>();
        cmd.put("id", id);
        cmd.put("label", label);
        cmd.put("message", message);
        return cmd;
    }

    @Operation(summary = "List AI conversations")
    @GetMapping("/conversations")
    public R<?> listConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AiConversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getStatus, 1)
                        .orderByDesc(AiConversation::getUpdatedAt));
        return R.ok(list);
    }

    @Operation(summary = "Get a conversation")
    @GetMapping("/conversations/{sessionId}")
    public R<?> getConversation(@PathVariable String sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId)
                        .eq(AiConversation::getUserId, userId)
                        .last("LIMIT 1"));
        if (conv == null) {
            throw BusinessException.notFound("Conversation not found");
        }

        List<AiMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conv.getId())
                        .orderByAsc(AiMessage::getCreatedAt));

        Map<String, Object> result = new HashMap<>();
        result.put("conversation", conv);
        result.put("messages", messages);
        return R.ok(result);
    }

    @Log("Delete AI conversation")
    @Operation(summary = "Archive conversation")
    @DeleteMapping("/conversations/{sessionId}")
    public R<?> deleteConversation(@PathVariable String sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId)
                        .eq(AiConversation::getUserId, userId)
                        .last("LIMIT 1"));
        if (conv == null) {
            throw BusinessException.notFound("Conversation not found");
        }

        conv.setStatus(0);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
        return R.ok("Conversation deleted");
    }

    @Log("Batch delete AI conversations")
    @Operation(summary = "Batch archive conversations")
    @DeleteMapping("/conversations/batch")
    public R<?> batchDeleteConversations(@RequestParam List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return R.fail("No session IDs provided");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        int count = 0;
        for (String sessionId : sessionIds) {
            AiConversation conv = conversationMapper.selectOne(
                    new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getSessionId, sessionId)
                            .eq(AiConversation::getUserId, userId)
                            .last("LIMIT 1"));
            if (conv != null) {
                conv.setStatus(0);
                conv.setUpdatedAt(LocalDateTime.now());
                conversationMapper.updateById(conv);
                count++;
            }
        }
        return R.ok(count + " conversations deleted");
    }

    @Operation(summary = "Get AI quota")
    @GetMapping("/quota")
    public R<?> getQuota() {
        Long userId = SecurityUtils.getCurrentUserId();
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

            if (!StringUtils.hasText(conversation.getContextType())
                    || "general".equals(conversation.getContextType())) {
                conversation.setContextType(detectIntent(req.getMessage()));
            }

            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", conversation.getSessionId());
            emitter.send(SseEmitter.event().name("session").data(sessionData));

            StringBuilder fullContent = new StringBuilder();
            StringBuilder fullReasoning = new StringBuilder();
            StringBuilder pendingTag = new StringBuilder();
            long startTime = System.currentTimeMillis();
            AtomicBoolean inThinking = new AtomicBoolean(false);
            AtomicBoolean emitterCompleted = new AtomicBoolean(false);
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
                    String reasoning = delta.path("reasoning_content").asText("");

                    // 1) 模型原生 reasoning_content 字段（DeepSeek-R1 / Qwen3 thinking 等）直接走独立事件
                    if (StringUtils.hasText(reasoning)) {
                        fullReasoning.append(reasoning);
                        Map<String, Object> reasoningData = new HashMap<>();
                        reasoningData.put("reasoning", reasoning);
                        emitter.send(SseEmitter.event().name("reasoning").data(reasoningData));
                    }

                    // 2) content 中内嵌的 <think>...</think> 块同样拆分为 reasoning 事件；可见部分走 message 事件
                    if (StringUtils.hasText(content)) {
                        ThinkSplit split = splitThinkingChunk(content, inThinking, pendingTag);
                        if (StringUtils.hasText(split.thinking)) {
                            fullReasoning.append(split.thinking);
                            Map<String, Object> reasoningData = new HashMap<>();
                            reasoningData.put("reasoning", split.thinking);
                            emitter.send(SseEmitter.event().name("reasoning").data(reasoningData));
                        }
                        if (StringUtils.hasText(split.visible)) {
                            fullContent.append(split.visible);
                            Map<String, Object> msgData = new HashMap<>();
                            msgData.put("content", split.visible);
                            emitter.send(SseEmitter.event().name("message").data(msgData));
                        }
                    }
                } catch (IllegalStateException ignore) {
                    emitterCompleted.set(true);
                } catch (Exception e) {
                    log.warn("Failed to process stream token: {}", e.getMessage());
                }
            })
                    .doOnComplete(() -> {
                        try {
                            // 冲刷未消耗完的标签前缀：如果流结束仍残留 "<" 或 "<th" 这类半个标签，按原文附加到可见区域
                            if (pendingTag.length() > 0) {
                                if (inThinking.get()) {
                                    fullReasoning.append(pendingTag);
                                } else {
                                    fullContent.append(pendingTag);
                                }
                                pendingTag.setLength(0);
                            }
                            String finalResponseRaw = finalizeAssistantText(fullContent.toString());
                            // 空内容 fallback 时仍可能要同步喊一次 LLM；这一步放在 netty 线程上会因 .block() 被拒，
                            // 所以把 DB 写入 + fallback 生成 + summarizeTitle 一起转到 aiChatExecutor 上执行。
                            long latency = System.currentTimeMillis() - startTime;
                            final String capturedInitial = finalResponseRaw;
                            aiChatExecutor.execute(() -> {
                                try {
                                    String finalResponse = capturedInitial;
                                    if (!StringUtils.hasText(finalResponse)) {
                                        String fallback = finalizeAssistantText(llmClient.chat(prompt, history));
                                        if (!StringUtils.hasText(fallback)) {
                                            fallback = buildLocalFallbackReply(userId, req.getMessage());
                                        }
                                        finalResponse = finalizeAssistantText(fallback);
                                        if (!emitterCompleted.get()) {
                                            try {
                                                Map<String, Object> fallbackMsg = new HashMap<>();
                                                fallbackMsg.put("content", finalResponse);
                                                emitter.send(SseEmitter.event().name("message").data(fallbackMsg));
                                            } catch (Exception ignored) {}
                                        }
                                    }

                                    saveAssistantMessage(conversation, req.getMessage(), finalResponse, fullReasoning.toString(), latency);
                                    incrementQuota(userId);

                                    if (emitterCompleted.compareAndSet(false, true)) {
                                        try {
                                            emitter.send(SseEmitter.event().name("done")
                                                    .data(Collections.singletonMap("latencyMs", latency)));
                                            emitter.complete();
                                        } catch (Exception ignored) {}
                                    }
                                } catch (Exception ex) {
                                    log.error("Failed to finalize chat stream", ex);
                                    if (emitterCompleted.compareAndSet(false, true)) {
                                        try { emitter.completeWithError(ex); } catch (Exception ignored) {}
                                    }
                                }
                            });
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
                        // 同样从 netty 迁移到 aiChatExecutor，避免 saveAssistantMessage 里的 LLM summarize 触发 block 异常
                        aiChatExecutor.execute(() -> {
                            try {
                                String fallback = finalizeAssistantText(buildLocalFallbackReply(userId, req.getMessage()));
                                String finalText = finalizeAssistantText(fullContent.toString());
                                if (StringUtils.hasText(finalText)) {
                                    finalText = finalText + "\n\n" + fallback;
                                } else {
                                    finalText = fallback;
                                }

                                if (!emitterCompleted.get()) {
                                    try {
                                        Map<String, Object> msgData = new HashMap<>();
                                        msgData.put("content", fallback);
                                        emitter.send(SseEmitter.event().name("message").data(msgData));
                                    } catch (Exception ignored) {}
                                }

                                saveAssistantMessage(conversation, req.getMessage(), finalText, fullReasoning.toString(),
                                        System.currentTimeMillis() - startTime);
                                incrementQuota(userId);
                                if (emitterCompleted.compareAndSet(false, true)) {
                                    try {
                                        emitter.send(SseEmitter.event().name("done").data(
                                                Collections.singletonMap("latencyMs", System.currentTimeMillis() - startTime)));
                                        emitter.complete();
                                    } catch (Exception ignored) {}
                                }
                            } catch (Exception ex) {
                                log.error("Failed to finalize on error", ex);
                                if (emitterCompleted.compareAndSet(false, true)) {
                                    try { emitter.completeWithError(error); } catch (Exception ignored) {}
                                }
                            }
                        });
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

    private void saveAssistantMessage(AiConversation conversation, String userMessage, String content, String reasoning, long latency) {
        saveAssistantMessage(conversation, userMessage, content, reasoning, null, latency);
    }

    private void saveAssistantMessage(AiConversation conversation, String userMessage, String content,
                                      String reasoning, List<Map<String, Object>> toolTrace, long latency) {
        String cleanedContent = finalizeAssistantText(content);
        if (!StringUtils.hasText(cleanedContent)) {
            cleanedContent = "抱歉，本次回答未生成有效内容，请重试。";
        }
        AiMessage assistantMsg = new AiMessage();
        assistantMsg.setConversationId(conversation.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(cleanedContent);
        assistantMsg.setContentType("text");
        assistantMsg.setLatencyMs((int) latency);
        assistantMsg.setCreatedAt(LocalDateTime.now());

        Map<String, Object> meta = new LinkedHashMap<>();
        if (StringUtils.hasText(reasoning)) {
            meta.put("reasoning", reasoning.trim());
        }
        if (toolTrace != null && !toolTrace.isEmpty()) {
            meta.put("toolTrace", toolTrace);
        }
        if (!meta.isEmpty()) {
            try {
                assistantMsg.setMetadata(objectMapper.writeValueAsString(meta));
            } catch (Exception e) {
                log.debug("Failed to attach metadata: {}", e.getMessage());
            }
        }
        messageMapper.insert(assistantMsg);

        int newCount = (conversation.getMessageCount() == null ? 0 : conversation.getMessageCount()) + 2;
        conversation.setMessageCount(newCount);
        conversation.setUpdatedAt(LocalDateTime.now());

        // 首次交互（刚写完第一轮 assistant）为会话生成一个 8-14 字的中文标题；失败则兜底截断首条用户消息。
        if (!StringUtils.hasText(conversation.getTitle())) {
            String summarized = newCount == 2 ? summarizeTitle(userMessage) : null;
            if (StringUtils.hasText(summarized)) {
                conversation.setTitle(summarized);
            } else {
                conversation.setTitle(userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage);
            }
        }
        conversationMapper.updateById(conversation);
    }

    /** 调一次小上下文 LLM 请求为新对话生成短标题，最多 30 个字，失败返回 null。 */
    private String summarizeTitle(String userMessage) {
        if (!StringUtils.hasText(userMessage) || !llmClient.isConfigured()) return null;
        try {
            List<Map<String, String>> msgs = new ArrayList<>();
            Map<String, String> m = new HashMap<>();
            m.put("role", "user");
            m.put("content", "请用 8 到 14 个中文字概括下面这个用户问题，只输出标题本身，不要引号、不要标点、不要前缀：\n" + userMessage);
            msgs.add(m);
            String title = llmClient.chat(
                    "你是对话标题生成助手，输出简短中文标题。",
                    msgs,
                    40,
                    12
            );
            if (!StringUtils.hasText(title)) return null;
            title = title.replaceAll("[\\s\\r\\n\"'“”‘’。！？：《》「」『』]+", "").trim();
            if (title.length() > 30) title = title.substring(0, 30);
            return StringUtils.hasText(title) ? title : null;
        } catch (Exception e) {
            log.debug("Title summarization failed: {}", e.getMessage());
            return null;
        }
    }

    private AiConversation getOrCreateConversation(Long userId, String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            AiConversation existing = conversationMapper.selectOne(
                    new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getSessionId, sessionId)
                            .eq(AiConversation::getUserId, userId)
                            .last("LIMIT 1"));
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
                        .last("LIMIT " + limit));
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

            if (INTENT_PATTERNS.get("skill").matcher(userMessage).find()
                    || INTENT_PATTERNS.get("career").matcher(userMessage).find()) {
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

    /** 流式 chunk 中的 <think>…</think> 状态机：返回 visible / thinking 两段，跨块续接由 inThinking 携带。 */
    private static class ThinkSplit {
        final String visible;
        final String thinking;
        ThinkSplit(String visible, String thinking) {
            this.visible = visible;
            this.thinking = thinking;
        }
    }

    private ThinkSplit splitThinkingChunk(String chunk, AtomicBoolean inThinking, StringBuilder pendingTag) {
        String input = chunk == null ? "" : chunk;
        if (pendingTag.length() > 0) {
            input = pendingTag.toString() + input;
            pendingTag.setLength(0);
        }
        if (input.isEmpty()) {
            return new ThinkSplit("", "");
        }
        StringBuilder visible = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        int idx = 0;
        int len = input.length();

        while (idx < len) {
            String seek = inThinking.get() ? "</think>" : "<think>";
            int hit = input.indexOf(seek, idx);
            if (hit >= 0) {
                StringBuilder target = inThinking.get() ? thinking : visible;
                target.append(input, idx, hit);
                idx = hit + seek.length();
                inThinking.set(!inThinking.get());
                continue;
            }
            // 没找到完整标签；检查尾部是否可能是标签前缀，若是则缓存到下一块再拼接。
            int safeEnd = len;
            int maxPrefix = Math.min(seek.length() - 1, len - idx);
            for (int p = maxPrefix; p >= 1; p--) {
                if (input.regionMatches(len - p, seek, 0, p)) {
                    safeEnd = len - p;
                    pendingTag.append(input, safeEnd, len);
                    break;
                }
            }
            StringBuilder target = inThinking.get() ? thinking : visible;
            target.append(input, idx, safeEnd);
            idx = len;
        }
        return new ThinkSplit(visible.toString(), thinking.toString());
    }

    /** 最终落库/兜底时的轻量清理：仅剥离残留 <think> 标签和两端空白，保留原 markdown。 */
    private String finalizeAssistantText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text
                .replaceAll("(?is)<think\\b[^>]*>.*?</think>", "")
                .replaceAll("(?is)<think\\b[^>]*>.*$", "")
                .replaceAll("</think>", "")
                .trim();
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
            throw BusinessException.of(429, "操作过于频繁，请稍后重试", "AI_QUOTA_EXCEEDED");
        }
    }

    private void validateAgentToolAccess(Integer roleType, String tool) {
        String normalizedTool = normalizeAgentTool(tool);
        Set<String> allowed = ROLE_ALLOWED_TOOLS.getOrDefault(
                roleType == null ? SysUser.ROLE_USER : roleType,
                ROLE_ALLOWED_TOOLS.get(SysUser.ROLE_USER)
        );
        if (!allowed.contains(normalizedTool)) {
            throw BusinessException.of(403, "权限不足，当前账号无法访问该能力", "AI_TOOL_FORBIDDEN");
        }
    }

    private String normalizeAgentTool(String tool) {
        if (!StringUtils.hasText(tool)) {
            return "auto";
        }
        String normalized = tool.trim().toUpperCase();
        switch (normalized) {
            case "RESUME_PARSE":
                return "profile_snapshot";
            case "PROFILE_IMPORT":
                return "profile_snapshot";
            case "JOB_MATCH":
                return "job_match";
            case "SKILL_GAP":
                return "skill_gap";
            case "SALARY_INSIGHT":
                return "salary_insight";
            case "COURSE_MATCH":
                return "course_supply_demand";
            case "SYLLABUS_ANALYZE":
                return "course_supply_demand";
            case "TEACHING_REFORM":
                return "teaching_reform";
            case "REPORT_ASSIST":
                return "career_path";
            case "OPS_INSIGHT":
                return "operations_dashboard";
            case "USER_GOVERNANCE":
                return "user_governance";
            case "DATA_QUALITY_CHECK":
                return "operations_dashboard";
            case "REPORT_GOVERNANCE":
                return "user_governance";
            default:
                return tool.trim().toLowerCase();
        }
    }

    private static Set<String> allowedTools(String... tools) {
        Set<String> values = new LinkedHashSet<>();
        Collections.addAll(values, tools);
        return values;
    }

    private void validateReadinessForAgent(Long userId, Integer roleType, String tool) {
        Map<String, Object> readiness = readinessService.buildReadiness(userId, roleType);
        if (Boolean.TRUE.equals(readiness.get("ready"))) {
            return;
        }
        String normalizedTool = normalizeAgentTool(tool);
        if ("auto".equals(normalizedTool) || "market_overview".equals(normalizedTool)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nextAction = (Map<String, Object>) readiness.get("nextAction");
        String actionLabel = nextAction == null ? "先完成前置数据准备" : String.valueOf(nextAction.getOrDefault("label", "先完成前置数据准备"));
        throw BusinessException.of(400,
                "当前前置数据未就绪，请先执行：" + actionLabel,
                "READINESS_REQUIRED");
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
}
