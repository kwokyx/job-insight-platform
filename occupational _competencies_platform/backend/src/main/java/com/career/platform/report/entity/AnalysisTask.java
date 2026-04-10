package com.career.platform.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析任务实体
 */
@Data
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
}
