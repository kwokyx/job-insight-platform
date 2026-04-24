package com.career.platform.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${career.ai.api-url}")
    private String apiUrl;

    @Value("${career.ai.api-key}")
    private String apiKey;

    @Value("${career.ai.model}")
    private String model;

    @Value("${career.ai.max-tokens}")
    private int maxTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 延迟初始化单例 WebClient，避免每次请求重复创建 */
    private volatile WebClient singletonClient;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(apiUrl);
    }

    public Flux<String> chatStream(String systemPrompt, List<Map<String, String>> messages) {
        if (!isConfigured()) {
            return Flux.error(new IllegalStateException("AI provider is not configured"));
        }

        Map<String, Object> body = buildRequestBody(systemPrompt, messages, true);
        WebClient client = buildClient();

        return client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(45))
                .flatMap(this::parseStreamChunk)
                .doOnError(e -> log.error("LLM stream request failed: {}", e.getMessage()));
    }

    public String chat(String systemPrompt, List<Map<String, String>> messages) {
        return chat(systemPrompt, messages, maxTokens, 25);
    }

    public String chat(String systemPrompt, List<Map<String, String>> messages, int requestMaxTokens, int timeoutSeconds) {
        if (!isConfigured()) {
            return "";
        }

        Map<String, Object> body = buildRequestBody(systemPrompt, messages, false);
        body.put("max_tokens", requestMaxTokens);
        WebClient client = buildClient();

        try {
            String response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block(Duration.ofSeconds(timeoutSeconds + 5L));
            return extractResponseText(response);
        } catch (Exception e) {
            log.error("LLM sync request failed: {}", e.getMessage());
            return "";
        }
    }

    public static class ToolCall {
        public final String id;
        public final String name;
        public final String argumentsJson;

        public ToolCall(String id, String name, String argumentsJson) {
            this.id = id;
            this.name = name;
            this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
        }
    }

    public static class ToolChatResult {
        public final String content;
        public final String reasoning;
        public final List<ToolCall> toolCalls;
        public final String finishReason;

        public ToolChatResult(String content, String reasoning, List<ToolCall> toolCalls, String finishReason) {
            this.content = content == null ? "" : content;
            this.reasoning = reasoning == null ? "" : reasoning;
            this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
            this.finishReason = finishReason == null ? "" : finishReason;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    /**
     * 发起一轮带 function calling 的对话。不做流式，返回 content 或 tool_calls。
     * messages 用 Object 值以容纳 role=assistant 的 tool_calls 数组 / role=tool 的 tool_call_id。
     * 针对 gpt-5 系列省略 temperature（该族只接受默认值 1）。
     */
    public ToolChatResult chatWithTools(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            int requestMaxTokens,
            int timeoutSeconds) {
        if (!isConfigured()) {
            return new ToolChatResult("", "", null, "not_configured");
        }

        List<Map<String, Object>> allMessages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            Map<String, Object> system = new LinkedHashMap<>();
            system.put("role", "system");
            system.put("content", systemPrompt);
            allMessages.add(system);
        }
        if (messages != null) {
            allMessages.addAll(messages);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", allMessages);
        body.put("max_tokens", requestMaxTokens > 0 ? requestMaxTokens : maxTokens);
        if (!isGpt5Family(model)) {
            body.put("temperature", 0.7);
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        body.put("stream", false);

        WebClient client = buildClient();
        try {
            String response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block(Duration.ofSeconds(timeoutSeconds + 5L));
            return parseToolChatResponse(response);
        } catch (Exception e) {
            log.error("LLM tool request failed: {}", e.getMessage());
            return new ToolChatResult("", "", null, "error:" + e.getMessage());
        }
    }

    /**
     * 带完整消息历史（含 tool_calls / role=tool）的流式请求。用于 function calling 最后一轮
     * 把工具结果交给模型后直接把正文流回前端。tools 参数为空，防止模型再发起新的工具调用。
     */
    public Flux<String> chatStreamWithHistory(String systemPrompt, List<Map<String, Object>> messages) {
        if (!isConfigured()) {
            return Flux.error(new IllegalStateException("AI provider is not configured"));
        }

        List<Map<String, Object>> allMessages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            Map<String, Object> system = new LinkedHashMap<>();
            system.put("role", "system");
            system.put("content", systemPrompt);
            allMessages.add(system);
        }
        if (messages != null) {
            allMessages.addAll(messages);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", allMessages);
        body.put("max_tokens", maxTokens);
        if (!isGpt5Family(model)) {
            body.put("temperature", 0.7);
        }
        body.put("stream", true);

        WebClient client = buildClient();
        return client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(60))
                .flatMap(this::parseStreamChunk)
                .doOnError(e -> log.error("LLM stream-with-history request failed: {}", e.getMessage()));
    }

    private boolean isGpt5Family(String modelName) {
        if (!StringUtils.hasText(modelName)) return false;
        String lower = modelName.toLowerCase();
        return lower.startsWith("gpt-5") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4");
    }

    private ToolChatResult parseToolChatResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return new ToolChatResult("", "", null, "empty");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String finishReason = choice.path("finish_reason").asText("");

            String content = readContentNode(message.path("content"));
            String reasoning = firstNonBlank(
                    message.path("reasoning").asText(""),
                    message.path("reasoning_content").asText(""),
                    message.path("thinking").asText("")
            );

            JsonNode toolCallsNode = message.path("tool_calls");
            List<ToolCall> toolCalls = new ArrayList<>();
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    String id = tc.path("id").asText("");
                    String name = tc.path("function").path("name").asText("");
                    String args = tc.path("function").path("arguments").asText("{}");
                    if (StringUtils.hasText(name)) {
                        toolCalls.add(new ToolCall(id, name, args));
                    }
                }
            }
            return new ToolChatResult(content, reasoning, toolCalls, finishReason);
        } catch (Exception e) {
            log.warn("Failed to parse LLM tool response: {}", e.getMessage());
            return new ToolChatResult("", "", null, "parse_error");
        }
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, List<Map<String, String>> messages, boolean stream) {
        List<Map<String, String>> allMessages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            Map<String, String> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", systemPrompt);
            allMessages.add(system);
        }
        if (messages != null) {
            allMessages.addAll(messages);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", allMessages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.7);
        body.put("stream", stream);
        return body;
    }

    private WebClient buildClient() {
        if (singletonClient == null) {
            synchronized (this) {
                if (singletonClient == null) {
                    HttpClient httpClient = HttpClient.create()
                            .responseTimeout(Duration.ofSeconds(120));
                    singletonClient = WebClient.builder()
                            .baseUrl(apiUrl)
                            .defaultHeader("Authorization", "Bearer " + apiKey)
                            .defaultHeader("Content-Type", "application/json")
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                            .build();
                }
            }
        }
        return singletonClient;
    }

    private Flux<String> parseStreamChunk(String chunk) {
        if (!StringUtils.hasText(chunk)) {
            return Flux.empty();
        }

        List<String> tokens = new ArrayList<>();
        String[] lines = chunk.split("\\r?\\n");
        for (String raw : lines) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String line = raw.trim();
            if (line.startsWith("data:")) {
                line = line.substring(5).trim();
            }
            if (!StringUtils.hasText(line) || "[DONE]".equals(line)) {
                continue;
            }
            String token = parseSingleEvent(line);
            if (token != null) {
                tokens.add(token);
            }
        }

        // Some providers can return pure JSON chunks without newline separation.
        if (tokens.isEmpty()) {
            String token = parseSingleEvent(chunk.trim());
            if (token != null) {
                tokens.add(token);
            }
        }
        return Flux.fromIterable(tokens);
    }

    private String parseSingleEvent(String jsonText) {
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            String content = firstNonBlank(
                    readContentNode(root.path("choices").path(0).path("delta").path("content")),
                    readContentNode(root.path("choices").path(0).path("message").path("content")),
                    readContentNode(root.path("content"))
            );
            String reasoning = firstNonBlank(
                    root.path("choices").path(0).path("delta").path("reasoning_content").asText(""),
                    root.path("choices").path(0).path("message").path("reasoning_content").asText(""),
                    root.path("reasoning_content").asText("")
            );
            if (!StringUtils.hasText(content) && !StringUtils.hasText(reasoning)) {
                return null;
            }
            return tokenJson(content, reasoning);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractResponseText(String response) {
        if (!StringUtils.hasText(response)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = firstNonBlank(
                    readContentNode(root.path("choices").path(0).path("message").path("content")),
                    readContentNode(root.path("choices").path(0).path("delta").path("content")),
                    readContentNode(root.path("content"))
            );
            return content == null ? "" : content;
        } catch (Exception e) {
            return response;
        }
    }

    private String readContentNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isTextual()) {
                    sb.append(item.asText(""));
                    continue;
                }
                sb.append(firstNonBlank(
                        item.path("text").asText(""),
                        item.path("content").asText(""),
                        item.path("output_text").asText("")
                ));
            }
            return sb.toString();
        }
        return firstNonBlank(
                node.path("text").asText(""),
                node.path("content").asText(""),
                node.path("output_text").asText(""),
                node.asText("")
        );
    }

    private String tokenJson(String content, String reasoning) {
        Map<String, String> token = new LinkedHashMap<>();
        token.put("content", content == null ? "" : content);
        token.put("reasoning_content", reasoning == null ? "" : reasoning);
        try {
            return objectMapper.writeValueAsString(token);
        } catch (Exception e) {
            return "{\"content\":\"\",\"reasoning_content\":\"\"}";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
