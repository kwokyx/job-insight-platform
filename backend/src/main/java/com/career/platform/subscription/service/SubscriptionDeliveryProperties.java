package com.career.platform.subscription.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionDeliveryProperties {

    private final SubscriptionMailService subscriptionMailService;

    public SubscriptionDeliveryProperties(SubscriptionMailService subscriptionMailService) {
        this.subscriptionMailService = subscriptionMailService;
    }

    public Map<String, Object> buildMeta() {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, Object>> channels = Collections.singletonList(
                channel(
                        "EMAIL",
                        "邮箱通知",
                        subscriptionMailService.isMailAvailable(),
                        subscriptionMailService.isMailAvailable()
                                ? "命中结果会发送到你的注册邮箱"
                                : "当前还没有配置邮件服务，请先补充 SMTP 参数"
                )
        );
        payload.put("channels", channels);
        payload.put("mailEnabled", subscriptionMailService.isMailAvailable());
        return payload;
    }

    private Map<String, Object> channel(String value, String label, boolean enabled, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("label", label);
        item.put("enabled", enabled);
        item.put("description", description);
        return item;
    }
}
