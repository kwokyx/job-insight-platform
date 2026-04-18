package com.career.platform.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
                            .responseTimeout(Duration.ofSeconds(25));
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
