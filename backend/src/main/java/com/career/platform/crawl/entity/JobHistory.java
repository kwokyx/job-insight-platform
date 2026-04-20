package com.career.platform.crawl.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位历史版本追踪
 */
@TableName("biz_job_history")
public class JobHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String snapshotData;    // JSON
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Integer isActive;
    private String changeType;      // SALARY_CHANGE / STATUS_CHANGE / DESC_CHANGE / NEW
    private LocalDateTime crawlTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getSnapshotData() { return snapshotData; }
    public void setSnapshotData(String snapshotData) { this.snapshotData = snapshotData; }
    public BigDecimal getSalaryMin() { return salaryMin; }
    public void setSalaryMin(BigDecimal salaryMin) { this.salaryMin = salaryMin; }
    public BigDecimal getSalaryMax() { return salaryMax; }
    public void setSalaryMax(BigDecimal salaryMax) { this.salaryMax = salaryMax; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public LocalDateTime getCrawlTime() { return crawlTime; }
    public void setCrawlTime(LocalDateTime crawlTime) { this.crawlTime = crawlTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
