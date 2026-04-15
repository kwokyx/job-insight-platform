package com.career.platform.subscription.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知
 */
@Data
@TableName("biz_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String content;
    private String notifyType;  // JOB_PUSH / REPORT_READY / SYSTEM
    private Long refId;
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
