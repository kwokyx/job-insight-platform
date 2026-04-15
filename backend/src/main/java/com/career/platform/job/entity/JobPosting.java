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

    @TableField("job_id_source")
    private String jobIdSource;
    private String title;

    @TableField(exist = false)
    private Long companyId;

    private String companyName;
    private String regionCode;
    private String provinceCode;
    private String cityCode;
    private Long jobCategoryId;

    @TableField("city")
    private String city;

    @TableField("industry_name")
    private String industryName;

    @TableField("education")
    private String education;

    @TableField("experience")
    private String experience;

    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    @TableField("salary_text")
    private String salaryText;

    @TableField("job_benefits")
    private String jobBenefits;

    @TableField(exist = false)
    private String jobLabels;

    @TableField("description")
    private String description;

    @TableField("source_url")
    private String sourceUrl;

    @TableField(exist = false)
    private String companySize;
    @TableField(exist = false)
    private String companyFinance;
    private LocalDate publishDate;
    private LocalDateTime crawlTime;

    @TableField(exist = false)
    private LocalDateTime crawlUpdateTime;
}
