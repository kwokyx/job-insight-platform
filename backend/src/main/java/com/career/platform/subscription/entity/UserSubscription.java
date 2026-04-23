package com.career.platform.subscription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户订阅实体
 */
@TableName("biz_user_subscription")
public class UserSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String subscriptionType;  // JOB_ALERT / REPORT_WEEKLY / SKILL_UPDATE
    @TableField(exist = false)
    private String filterCriteria;    // JSON alias for filterConfig
    private String filterConfig;      // JSON (alias for controller usage)
    private Integer isActive;
    @TableField(exist = false)
    private String pushChannel;       // EMAIL / IN_APP alias for channel
    private String channel;           // alias for controller usage
    private LocalDateTime lastPushedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }

    public String getFilterCriteria() {
        return filterCriteria != null ? filterCriteria : filterConfig;
    }

    public void setFilterCriteria(String filterCriteria) {
        this.filterCriteria = filterCriteria;
        if (filterConfig == null) {
            this.filterConfig = filterCriteria;
        }
    }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getPushChannel() {
        return pushChannel != null ? pushChannel : channel;
    }

    public void setPushChannel(String pushChannel) {
        this.pushChannel = pushChannel;
        if (channel == null) {
            this.channel = pushChannel;
        }
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFilterConfig() { return filterConfig; }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
        if (this.filterCriteria == null) {
            this.filterCriteria = filterConfig;
        }
    }

    public String getChannel() { return channel; }

    public void setChannel(String channel) {
        this.channel = channel;
        if (this.pushChannel == null) {
            this.pushChannel = channel;
        }
    }

    public LocalDateTime getLastPushedAt() { return lastPushedAt; }
    public void setLastPushedAt(LocalDateTime lastPushedAt) { this.lastPushedAt = lastPushedAt; }
}
