package com.career.platform.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分析报告实体
 */
@Data
@TableName("biz_analysis_report")
public class AnalysisReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String reportName;
    private String reportType;      // INDUSTRY/SALARY/SKILL/COMPREHENSIVE
    private String reportFormat;    // HTML/PDF/EXCEL
    private String description;
    private String analysisData;    // JSON字符串
    private String fileUrl;
    private Long fileSize;
    private Integer isPublic;       // 0/1
    private Integer viewCount;
    private Integer downloadCount;
    private Long generatedBy;
    private LocalDateTime generatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
