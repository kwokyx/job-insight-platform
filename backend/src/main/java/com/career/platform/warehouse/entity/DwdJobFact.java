package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数仓明细宽表 (DWD层)
 */
@Data
@TableName("dwd_job_fact")
public class DwdJobFact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String title;
    private String companyName;
    private String cityStd;
    private String province;
    private String industryStd;
    private String industryL1;
    private String educationStd;
    private Integer experienceMin;
    private Integer experienceMax;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private BigDecimal salaryAvg;
    private String skillTags;   // JSON array
    private LocalDate publishDate;
    private String sourceSite;
    private LocalDateTime etlTime;
}
