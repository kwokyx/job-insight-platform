package com.career.platform.crawl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位历史版本追踪
 */
@Data
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
}
