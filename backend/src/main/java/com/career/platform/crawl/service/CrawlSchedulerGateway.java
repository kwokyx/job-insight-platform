package com.career.platform.crawl.service;

import com.career.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CrawlSchedulerGateway {

    private final WebClient crawlSchedulerWebClient;
    private final ObjectMapper objectMapper;

    public CrawlSchedulerGateway(@Qualifier("crawlSchedulerWebClient") WebClient crawlSchedulerWebClient,
                                 ObjectMapper objectMapper) {
        this.crawlSchedulerWebClient = crawlSchedulerWebClient;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listTasks(Map<String, Object> query) {
        return get("/tasks/", query);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createTask(Map<String, Object> payload) {
        return post("/tasks/", payload);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTask(String taskId) {
        return get("/tasks/" + taskId, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> startTask(String taskId) {
        return post("/tasks/" + taskId + "/start", new LinkedHashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> pauseTask(String taskId) {
        return post("/tasks/" + taskId + "/pause", new LinkedHashMap<String, Object>());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> taskStats(String taskId) {
        return get("/tasks/" + taskId + "/stats", null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> taskShards(String taskId, Map<String, Object> query) {
        return get("/tasks/" + taskId + "/shards", query);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> taskLogs(Map<String, Object> query) {
        return get("/logs/", query);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchZhaopinAuthStatus() {
        return get("/config/zhaopin/auth/status", null);
    }

    private Map<String, Object> get(String path, Map<String, Object> query) {
        try {
            return crawlSchedulerWebClient.get()
                    .uri(uriBuilder -> {
                        UriBuilder builder = uriBuilder.path(path);
                        if (query != null) {
                            query.forEach((key, value) -> {
                                if (value != null && !"".equals(String.valueOf(value).trim())) {
                                    builder.queryParam(key, value);
                                }
                            });
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            throw BusinessException.of(502, "调度中心不可用: " + e.getMessage());
        }
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload == null ? new LinkedHashMap<String, Object>() : payload);
            return crawlSchedulerWebClient.post()
                    .uri(path)
                    .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            throw BusinessException.of(502, "调度中心不可用: " + e.getMessage());
        }
    }
}
