package com.career.platform.subscription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_user_subscription")
public class UserSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String subscriptionType;
    private String filterConfig;
    private String channel;
    private Integer isActive;
    private LocalDateTime lastPushedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
