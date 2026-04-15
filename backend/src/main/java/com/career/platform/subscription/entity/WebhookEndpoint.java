package com.career.platform.subscription.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook端点注册
 */
@Data
@TableName("biz_webhook_endpoint")
public class WebhookEndpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String endpointUrl;
    private String secretKey;
    private String eventTypes;      // JSON array
    private Integer isActive;
    private LocalDateTime lastTriggered;
    private Integer failCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
