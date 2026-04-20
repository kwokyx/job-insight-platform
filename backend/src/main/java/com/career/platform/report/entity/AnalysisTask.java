package com.career.platform.report.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 分析任务实体
 */
@TableName("biz_analysis_task")
public class AnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;
    private String taskType;       // SALARY/SKILL/TREND/INDUSTRY/COMPREHENSIVE
    private String params;         // JSON
    private String status;         // PENDING/RUNNING/SUCCESS/FAILED
    private Integer progress;      // 0-100
    private String resultSummary;  // JSON
    private String resultFileUrl;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getResultFileUrl() { return resultFileUrl; }
    public void setResultFileUrl(String resultFileUrl) { this.resultFileUrl = resultFileUrl; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
