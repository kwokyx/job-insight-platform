package com.career.platform.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.subscription.entity.WebhookDelivery;
import com.career.platform.subscription.entity.WebhookEndpoint;
import com.career.platform.subscription.mapper.WebhookDeliveryMapper;
import com.career.platform.subscription.mapper.WebhookEndpointMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEndpointMapper webhookEndpointMapper;
    private final WebhookDeliveryMapper webhookDeliveryMapper;
    private final ObjectMapper objectMapper;

    @Value("${career.webhook.max-retries:3}")
    private int maxRetries;

    public WebhookService(WebhookEndpointMapper webhookEndpointMapper, WebhookDeliveryMapper webhookDeliveryMapper,
                          ObjectMapper objectMapper) {
        this.webhookEndpointMapper = webhookEndpointMapper;
        this.webhookDeliveryMapper = webhookDeliveryMapper;
        this.objectMapper = objectMapper;
    }

    public void deliverJobMatches(Long userId, List<JobPosting> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }

        List<WebhookEndpoint> endpoints = webhookEndpointMapper.selectList(
                new LambdaQueryWrapper<WebhookEndpoint>()
                        .eq(WebhookEndpoint::getUserId, userId)
                        .eq(WebhookEndpoint::getIsActive, 1)
        );

        for (WebhookEndpoint endpoint : endpoints) {
            deliver(endpoint, "JOB_PUSH", matches);
        }
    }

    private void deliver(WebhookEndpoint endpoint, String eventType, List<JobPosting> matches) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("endpointId", endpoint.getId());
        payload.put("items", matches);
        payload.put("deliveredAt", LocalDateTime.now());

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEndpointId(endpoint.getId());
        delivery.setEventType(eventType);
        delivery.setAttempt(1);
        delivery.setStatus("PENDING");
        delivery.setCreatedAt(LocalDateTime.now());

        try {
            String body = objectMapper.writeValueAsString(payload);
            delivery.setPayload(body);

            long started = System.currentTimeMillis();
            String responseBody = WebClient.builder()
                    .baseUrl(endpoint.getEndpointUrl())
                    .defaultHeader("X-Signature", sign(body, endpoint.getSecretKey()))
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            delivery.setHttpStatus(200);
            delivery.setResponseBody(truncate(responseBody));
            delivery.setResponseTime((int) (System.currentTimeMillis() - started));
            delivery.setStatus("SUCCESS");
            endpoint.setLastTriggered(LocalDateTime.now());
            endpoint.setFailCount(0);
            webhookEndpointMapper.updateById(endpoint);
        } catch (Exception e) {
            delivery.setHttpStatus(500);
            delivery.setResponseBody(truncate(e.getMessage()));
            delivery.setStatus("FAILED");
            endpoint.setFailCount((endpoint.getFailCount() == null ? 0 : endpoint.getFailCount()) + 1);
            webhookEndpointMapper.updateById(endpoint);
            log.warn("Webhook delivery failed for endpoint {}: {}", endpoint.getId(), e.getMessage());
        }

        webhookDeliveryMapper.insert(delivery);
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
