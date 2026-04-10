package com.career.platform.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 大模型 API 客户端
 * 支持：DeepSeek / 通义千问 / OpenAI 兼容格式
 * 统一 SSE 流式输出
 */
@Slf4j
@Component
public class LlmClient {

    @Value("${career.ai.api-url}")
    private String apiUrl;

    @Value("${career.ai.api-key}")
    private String apiKey;

    @Value("${career.ai.model}")
    private String model;

    @Value("${career.ai.max-tokens}")
    private int maxTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式对话 — 返回 token 流
     *
     * @param systemPrompt 系统提示词
     * @param messages      历史消息 [{role, content}]
     * @return Flux<String> 每个元素是一个文本片段
     */
    public Flux<String> chatStream(String systemPrompt, List<Map<String, String>> messages) {
        // 构建请求体
        List<Map<String, String>> allMessages = new ArrayList<>();

        // System prompt
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            allMessages.add(sysMsg);
        }

        // 历史消息
        allMessages.addAll(messages);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", allMessages);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);
        body.put("temperature", 0.7);

        WebClient client = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        return client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.trim().isEmpty() && !line.equals("[DONE]"))
                .map(this::extractContent)
                .filter(s -> s != null && !s.isEmpty())
                .onErrorResume(e -> {
                    log.error("LLM API 调用失败: {}", e.getMessage());
                    return Flux.just("[AI 服务暂时不可用，请稍后重试]");
                });
    }

    /**
     * 非流式对话 — 返回完整回复
     */
    public String chat(String systemPrompt, List<Map<String, String>> messages) {
        List<Map<String, String>> allMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            allMessages.add(sysMsg);
        }
        allMessages.addAll(messages);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", allMessages);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        body.put("temperature", 0.7);

        WebClient client = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        try {
            String response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("LLM API 同步调用失败: {}", e.getMessage());
            return "AI 服务暂时不可用，请稍后重试";
        }
    }

    /**
     * 从 SSE data 行解析增量内容
     * 格式: data: {"choices":[{"delta":{"content":"xxx"}}]}
     */
    private String extractContent(String data) {
        try {
            String json = data;
            if (json.startsWith("data:")) {
                json = json.substring(5).trim();
            }
            if (json.equals("[DONE]") || json.trim().isEmpty()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode delta = root.path("choices").path(0).path("delta");
            return delta.toString();
        } catch (Exception e) {
            // 非 JSON 行，忽略
            return null;
        }
    }
}
