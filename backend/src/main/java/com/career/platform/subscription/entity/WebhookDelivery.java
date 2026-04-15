package com.career.platform.subscription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_webhook_delivery")
public class WebhookDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long endpointId;
    private String eventType;
    private String payload;
    private Integer httpStatus;
    private String responseBody;
    private Integer responseTime;
    private Integer attempt;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
