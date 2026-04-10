package com.career.platform.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.ai.client.LlmClient;
import com.career.platform.ai.entity.AiConversation;
import com.career.platform.ai.entity.AiMessage;
import com.career.platform.ai.mapper.AiConversationMapper;
import com.career.platform.ai.mapper.AiMessageMapper;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 助手控制器
 * SSE 流式返回大模型回复
 */
@Slf4j
@Tag(name = "AI 助手", description = "智能对话、会话管理、调用配额")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final LlmClient llmClient;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final JobPostingMapper jobMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${career.ai.daily-quota}")
    private int dailyQuota;

    // 系统提示词
    private static final String SYSTEM_PROMPT =
            "你是「职业能力大数据服务平台」的 AI 助手。你的职责是：\n" +
            "1. 帮助用户分析就业市场趋势和薪资水平\n" +
            "2. 根据用户技能和背景提供职业规划建议\n" +
            "3. 解读行业数据和就业报告\n" +
            "4. 推荐合适的岗位和技能提升方向\n\n" +
            "你的回答应该：\n" +
            "- 专业、客观、有数据支撑\n" +
            "- 对于薪资问题，尽量引用平台数据\n" +
            "- 对于职业建议，结合行业趋势分析\n" +
            "- 语言亲切友好，适合大学生用户群体\n\n" +
            "如果被问到与就业/职业/教育无关的问题，礼貌地引导回主题。";

    // 意图识别关键词模式
    private static final Map<String, Pattern> INTENT_PATTERNS;
    static {
        INTENT_PATTERNS = new HashMap<>();
        INTENT_PATTERNS.put("city", Pattern.compile("(北京|上海|广州|深圳|杭州|成都|南京|武汉|西安|重庆|苏州|天津|合肥|长沙|郑州|东莞|青岛|佛山|宁波|厦门)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("salary", Pattern.compile("(薪资|工资|薪水|待遇|收入|月薪|年薪|钱|多少k|报酬)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("skill", Pattern.compile("(python|java|javascript|c\\+\\+|go|rust|vue|react|spring|node|tensorflow|pytorch|sql|docker|kubernetes|k8s|前端|后端|全栈|大数据|人工智能|机器学习|深度学习|数据分析|数据挖掘|运维|devops|算法|嵌入式|android|ios|flutter|微服务|云计算)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("education", Pattern.compile("(学历|大专|本科|硕士|博士|研究生|专科|MBA|毕业)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("industry", Pattern.compile("(互联网|金融|教育|医疗|电商|游戏|制造|汽车|房地产|物流|传媒|新能源|半导体|芯片|生物|it|计算机|软件|通信|电子|人工智能行业)", Pattern.CASE_INSENSITIVE));
        INTENT_PATTERNS.put("career", Pattern.compile("(职业|规划|发展|转行|前景|趋势|就业|求职|面试|简历|晋升|跳槽)", Pattern.CASE_INSENSITIVE));
    }

    // ─── AI 对话 (SSE 流式) ─────────────

    @Data
    public static class ChatRequest {
        @NotBlank(message = "消息不能为空")
        private String message;
        private String sessionId;   // 为空则创建新会话
    }

    @Operation(summary = "AI 对话（SSE 流式返回）")
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@Valid @RequestBody ChatRequest req) {
        Long userId = getCurrentUserId();

        // 检查配额
        checkQuota(userId);

        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时

        // 异步处理
        new Thread(() -> {
            try {
                // 1. 获取或创建会话
                AiConversation conversation = getOrCreateConversation(userId, req.getSessionId());

                // 2. 保存用户消息
                saveMessage(conversation.getId(), "user", req.getMessage());

                // 3. 加载历史消息（最近10条）
                List<Map<String, String>> history = loadHistory(conversation.getId(), 10);

                // 4. 构建增强 prompt（注入平台数据）
                String enhancedPrompt = buildEnhancedPrompt(req.getMessage());

                // 5. 根据意图识别设置会话 contextType
                String intent = detectIntent(req.getMessage());
                if (conversation.getContextType() == null || "general".equals(conversation.getContextType())) {
                    conversation.setContextType(intent);
                }

                // 6. 先发送 sessionId 事件
                Map<String, Object> sessionData = new HashMap<>();
                sessionData.put("sessionId", conversation.getSessionId());
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data(sessionData));

                // 7. 流式调用大模型
                StringBuilder fullResponse = new StringBuilder();
                long startTime = System.currentTimeMillis();

                llmClient.chatStream(enhancedPrompt, history)
                        .doOnNext(tokenJson -> {
                            try {
                                com.fasterxml.jackson.databind.JsonNode deltaParams = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tokenJson);
                                String c = deltaParams.path("content").asText("");
                                String r = deltaParams.path("reasoning_content").asText("");
                                fullResponse.append(c);
                                // 也将 reasoning 追加或前端自己处理
                                Map<String, Object> msgData = new HashMap<>();
                                msgData.put("content", c);
                                msgData.put("reasoning_content", r);
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(msgData));
                            } catch (Exception e) {
                                log.warn("SSE 解析或发送失败: {}", e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                long latency = System.currentTimeMillis() - startTime;

                                // 保存 assistant 完整回复
                                AiMessage assistantMsg = new AiMessage();
                                assistantMsg.setConversationId(conversation.getId());
                                assistantMsg.setRole("assistant");
                                assistantMsg.setContent(fullResponse.toString());
                                assistantMsg.setContentType("text");
                                assistantMsg.setLatencyMs((int) latency);
                                assistantMsg.setCreatedAt(LocalDateTime.now());
                                messageMapper.insert(assistantMsg);

                                // 更新会话
                                conversation.setMessageCount(conversation.getMessageCount() + 2);
                                conversation.setUpdatedAt(LocalDateTime.now());
                                if (conversation.getTitle() == null || conversation.getTitle().trim().isEmpty()) {
                                    conversation.setTitle(req.getMessage().length() > 30
                                            ? req.getMessage().substring(0, 30) + "..."
                                            : req.getMessage());
                                }
                                conversationMapper.updateById(conversation);

                                // 增加配额计数
                                incrementQuota(userId);

                                // 发送完成事件
                                Map<String, Object> doneData = new HashMap<>();
                                doneData.put("latencyMs", latency);
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data(doneData));
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("SSE 完成处理失败", e);
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(e -> {
                            log.error("LLM 流式调用错误", e);
                            try {
                                Map<String, Object> errData = new HashMap<>();
                                errData.put("message", "AI 服务出错: " + e.getMessage());
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(errData));
                            } catch (IOException ignored) {}
                            emitter.completeWithError(e);
                        })
                        .subscribe();

            } catch (Exception e) {
                log.error("AI 对话处理失败", e);
                try {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", e.getMessage());
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(errData));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    // ─── 会话列表 ────────────────────────

    @Operation(summary = "获取会话列表")
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

    // ─── 会话历史消息 ────────────────────

    @Operation(summary = "获取会话历史消息")
    @GetMapping("/conversations/{sessionId}")
    public R<?> getConversation(@PathVariable String sessionId) {
        Long userId = getCurrentUserId();
        AiConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId)
                        .eq(AiConversation::getUserId, userId)
        );
        if (conv == null) {
            throw BusinessException.notFound("会话不存在");
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

    // ─── 删除会话 ────────────────────────

    @Log("删除 AI 会话")
    @Operation(summary = "删除（归档）会话")
    @DeleteMapping("/conversations/{sessionId}")
    public R<?> deleteConversation(@PathVariable String sessionId) {
        Long userId = getCurrentUserId();
        AiConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getSessionId, sessionId)
                        .eq(AiConversation::getUserId, userId)
        );
        if (conv == null) {
            throw BusinessException.notFound("会话不存在");
        }

        conv.setStatus(0);  // 归档
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        return R.ok("会话已删除");
    }

    // ─── 配额查询 ────────────────────────

    @Operation(summary = "查询 AI 调用剩余次数")
    @GetMapping("/quota")
    public R<?> getQuota() {
        Long userId = getCurrentUserId();
        String key = "ai:quota:" + userId + ":" + LocalDate.now();
        int used = parseQuotaValue(redisTemplate.opsForValue().get(key));

        Map<String, Object> quota = new HashMap<>();
        quota.put("used", used);
        quota.put("limit", dailyQuota);
        quota.put("remaining", Math.max(0, dailyQuota - used));
        return R.ok(quota);
    }

    // ─── 内部方法 ────────────────────────

    private AiConversation getOrCreateConversation(Long userId, String sessionId) {
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            AiConversation existing = conversationMapper.selectOne(
                    new LambdaQueryWrapper<AiConversation>()
                            .eq(AiConversation::getSessionId, sessionId)
                            .eq(AiConversation::getUserId, userId)
            );
            if (existing != null) return existing;
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
                .map(m -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 意图识别 — 从用户消息判断对话类型
     */
    private String detectIntent(String message) {
        if (message == null) return "general";

        // 按优先级检测
        if (INTENT_PATTERNS.get("salary").matcher(message).find()) return "salary_analysis";
        if (INTENT_PATTERNS.get("career").matcher(message).find()) return "career_advice";
        if (INTENT_PATTERNS.get("skill").matcher(message).find()) return "skill_analysis";
        if (INTENT_PATTERNS.get("industry").matcher(message).find()) return "industry_analysis";
        if (INTENT_PATTERNS.get("city").matcher(message).find()) return "city_analysis";
        if (INTENT_PATTERNS.get("education").matcher(message).find()) return "education_analysis";

        return "general";
    }

    /**
     * 构建增强提示词：根据意图注入多维度平台统计数据到 system prompt
     */
    private String buildEnhancedPrompt(String userMessage) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);

        try {
            // 基础概览数据（始终注入）
            Map<String, Object> stats = jobMapper.overviewStats();
            long totalJobs = jobMapper.selectCount(null);
            sb.append("\n\n## 当前平台数据概览\n");
            sb.append("- 平台共有 ").append(totalJobs).append(" 个职位\n");
            if (stats.get("avgSalaryMin") != null) {
                sb.append("- 平均薪资范围: ").append(stats.get("avgSalaryMin"))
                  .append("K ~ ").append(stats.get("avgSalaryMax")).append("K/月\n");
            }

            // ── 热门技能数据 ──
            Matcher skillMatcher = INTENT_PATTERNS.get("skill").matcher(userMessage);
            if (skillMatcher.find() || INTENT_PATTERNS.get("career").matcher(userMessage).find()) {
                List<Map<String, Object>> topSkills = jobMapper.topSkills(10);
                sb.append("\n### 热门技能 TOP10\n");
                for (int i = 0; i < topSkills.size(); i++) {
                    Map<String, Object> s = topSkills.get(i);
                    sb.append(i + 1).append(". ").append(s.get("skill")).append("（").append(s.get("count")).append("个岗位）\n");
                }
            }

            // ── 城市数据 ──
            Matcher cityMatcher = INTENT_PATTERNS.get("city").matcher(userMessage);
            if (cityMatcher.find()) {
                List<Map<String, Object>> cityData = jobMapper.aggregateByCity(10);
                sb.append("\n### 热门城市薪资排行\n");
                for (Map<String, Object> c : cityData) {
                    sb.append("- ").append(c.get("city")).append(": ").append(c.get("count")).append("个岗位, 均薪 ").append(c.get("avgSalary")).append("K\n");
                }
            }

            // ── 薪资数据 ──
            if (INTENT_PATTERNS.get("salary").matcher(userMessage).find()) {
                List<Map<String, Object>> eduData = jobMapper.aggregateByEducation();
                sb.append("\n### 学历-薪资对照\n");
                for (Map<String, Object> e : eduData) {
                    sb.append("- ").append(e.get("education")).append(": 均薪 ").append(e.get("avgSalary")).append("K, ").append(e.get("count")).append("个岗位\n");
                }

                List<Map<String, Object>> expData = jobMapper.aggregateByExperience();
                sb.append("\n### 经验-薪资对照\n");
                for (Map<String, Object> e : expData) {
                    sb.append("- ").append(e.get("experience")).append(": 均薪 ").append(e.get("avgSalary")).append("K, ").append(e.get("count")).append("个岗位\n");
                }
            }

            // ── 行业数据 ──
            if (INTENT_PATTERNS.get("industry").matcher(userMessage).find()) {
                List<Map<String, Object>> indData = jobMapper.aggregateByIndustry(10);
                sb.append("\n### 热门行业岗位数 TOP10\n");
                for (int i = 0; i < indData.size(); i++) {
                    Map<String, Object> ind = indData.get(i);
                    sb.append(i + 1).append(". ").append(ind.get("industry")).append(": ").append(ind.get("count")).append("个, 均薪 ").append(ind.get("avgSalary")).append("K\n");
                }
            }

        } catch (Exception e) {
            log.warn("注入平台数据失败: {}", e.getMessage());
        }

        return sb.toString();
    }

    private void checkQuota(Long userId) {
        String key = "ai:quota:" + userId + ":" + LocalDate.now();
        int used = parseQuotaValue(redisTemplate.opsForValue().get(key));
        if (used >= dailyQuota) {
            throw BusinessException.of(429, "今日 AI 对话次数已用完（" + dailyQuota + "次/天）");
        }
    }

    private void incrementQuota(Long userId) {
        String key = "ai:quota:" + userId + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 25, TimeUnit.HOURS);
    }

    private int parseQuotaValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid AI quota value in Redis: {}", value);
            return 0;
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("请先登录");
        }
        return (Long) auth.getPrincipal();
    }
}
