package com.career.platform.crawl.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 数据源配置
 */
@TableName("biz_data_source")
public class DataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceName;
    private String sourceCode;
    private String baseUrl;
    private String crawlStrategy;   // JSON
    private Integer isActive;
    private LocalDateTime lastCrawlAt;
    private Long totalRecords;
    private String healthStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getCrawlStrategy() { return crawlStrategy; }
    public void setCrawlStrategy(String crawlStrategy) { this.crawlStrategy = crawlStrategy; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getLastCrawlAt() { return lastCrawlAt; }
    public void setLastCrawlAt(LocalDateTime lastCrawlAt) { this.lastCrawlAt = lastCrawlAt; }
    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
