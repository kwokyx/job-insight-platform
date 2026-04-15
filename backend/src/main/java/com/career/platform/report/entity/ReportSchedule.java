package com.career.platform.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_report_schedule")
public class ReportSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scheduleName;
    private String reportType;
    private String cronExpr;
    private String params;
    private Integer isActive;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
