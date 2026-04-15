package com.career.platform.subscription.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.subscription.entity.WebhookDelivery;
import com.career.platform.subscription.entity.WebhookEndpoint;
import com.career.platform.subscription.mapper.WebhookDeliveryMapper;
import com.career.platform.subscription.mapper.WebhookEndpointMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Webhook", description = "Register and monitor webhook endpoints")
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookEndpointMapper webhookMapper;
    private final WebhookDeliveryMapper webhookDeliveryMapper;

    @Operation(summary = "Webhook list")
    @GetMapping
    public R<List<WebhookEndpoint>> list() {
        Long userId = getCurrentUserId();
        List<WebhookEndpoint> list = webhookMapper.selectList(
                new LambdaQueryWrapper<WebhookEndpoint>()
                        .eq(WebhookEndpoint::getUserId, userId)
                        .orderByDesc(WebhookEndpoint::getCreatedAt)
        );
        list.forEach(endpoint -> endpoint.setSecretKey(maskSecret(endpoint.getSecretKey())));
        return R.ok(list);
    }

    @Operation(summary = "Webhook delivery history")
    @GetMapping("/{id}/deliveries")
    public R<List<WebhookDelivery>> deliveries(@PathVariable Long id) {
        WebhookEndpoint endpoint = webhookMapper.selectById(id);
        if (endpoint == null || !endpoint.getUserId().equals(getCurrentUserId())) {
            throw BusinessException.notFound("Webhook not found");
        }
        List<WebhookDelivery> deliveries = webhookDeliveryMapper.selectList(
                new LambdaQueryWrapper<WebhookDelivery>()
                        .eq(WebhookDelivery::getEndpointId, id)
                        .orderByDesc(WebhookDelivery::getCreatedAt)
        );
        return R.ok(deliveries);
    }

    @Data
    public static class CreateWebhookRequest {
        @NotBlank(message = "endpointUrl is required")
        private String endpointUrl;
        private String eventTypes;
    }

    @Log("Register webhook")
    @Operation(summary = "Register webhook endpoint")
    @PostMapping
    public R<?> create(@Valid @RequestBody CreateWebhookRequest req) {
        Long userId = getCurrentUserId();

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setUserId(userId);
        endpoint.setEndpointUrl(req.getEndpointUrl());
        endpoint.setSecretKey(java.util.UUID.randomUUID().toString().replace("-", ""));
        endpoint.setEventTypes(req.getEventTypes());
        endpoint.setIsActive(1);
        endpoint.setFailCount(0);
        endpoint.setCreatedAt(LocalDateTime.now());
        webhookMapper.insert(endpoint);

        return R.ok("Webhook registered", endpoint);
    }

    @Log("Delete webhook")
    @Operation(summary = "Delete webhook endpoint")
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        WebhookEndpoint endpoint = webhookMapper.selectById(id);
        if (endpoint == null || !endpoint.getUserId().equals(getCurrentUserId())) {
            throw BusinessException.notFound("Webhook not found");
        }
        webhookMapper.deleteById(id);
        return R.ok("Webhook deleted");
    }

    @Log("Toggle webhook")
    @Operation(summary = "Enable or disable webhook")
    @PutMapping("/{id}/toggle")
    public R<?> toggle(@PathVariable Long id) {
        WebhookEndpoint endpoint = webhookMapper.selectById(id);
        if (endpoint == null || !endpoint.getUserId().equals(getCurrentUserId())) {
            throw BusinessException.notFound("Webhook not found");
        }
        endpoint.setIsActive(endpoint.getIsActive() == 1 ? 0 : 1);
        webhookMapper.updateById(endpoint);
        return R.ok("Webhook status updated");
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 8) + "****";
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Please login first");
        }
        return (Long) auth.getPrincipal();
    }
}
