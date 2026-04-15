package com.career.platform.open.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 管理
 */
@Data
@TableName("sys_api_key")
public class ApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String apiKey;
    private String keyName;
    private String permissions;     // JSON
    private Integer rateLimitQps;
    private Integer dailyQuota;
    private Integer isActive;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
