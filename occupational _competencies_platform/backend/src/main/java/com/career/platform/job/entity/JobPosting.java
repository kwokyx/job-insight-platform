package com.career.platform.job.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 职位数据实体
 */
@Data
@TableName("biz_job_posting")
public class JobPosting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobIdSource;
    private String title;
    private Long companyId;
    private String companyName;
    private String region;
    private String city;
    private String district;
    private Long industryId;
    private String industryName;
    private String education;
    private String experience;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryUnit;
    private String salaryText;
    private String employmentType;
    private String description;
    private String requirements;
    private String sourceSite;
    private String sourceUrl;
    private LocalDate publishDate;
    private LocalDateTime crawlTime;
    private Integer dataQuality;
    private Integer isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
