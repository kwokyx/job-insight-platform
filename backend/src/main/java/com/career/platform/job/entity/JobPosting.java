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

    @TableField("url_obj_id")
    private String jobIdSource;
    private String title;

    @TableField(exist = false)
    private Long companyId;

    private String companyName;
    private String regionCode;
    private String provinceCode;
    private String cityCode;
    private Long jobCategoryId;

    @TableField("job_city")
    private String city;

    @TableField("job_classification")
    private String industryName;

    @TableField("education_need")
    private String education;

    @TableField("experience_year")
    private String experience;

    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    @TableField("salary_raw")
    private String salaryText;

    @TableField("job_welfare")
    private String jobBenefits;

    @TableField("job_labels")
    private String jobLabels;

    @TableField("position_info")
    private String description;

    @TableField("url")
    private String sourceUrl;

    private String companySize;
    private String companyFinance;
    private LocalDate publishDate;
    private LocalDateTime crawlTime;

    private LocalDateTime crawlUpdateTime;
}
