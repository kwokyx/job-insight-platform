package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数仓明细宽表 (DWD层)
 */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCityStd() { return cityStd; }
    public void setCityStd(String cityStd) { this.cityStd = cityStd; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getIndustryStd() { return industryStd; }
    public void setIndustryStd(String industryStd) { this.industryStd = industryStd; }

    public String getIndustryL1() { return industryL1; }
    public void setIndustryL1(String industryL1) { this.industryL1 = industryL1; }

    public String getEducationStd() { return educationStd; }
    public void setEducationStd(String educationStd) { this.educationStd = educationStd; }

    public Integer getExperienceMin() { return experienceMin; }
    public void setExperienceMin(Integer experienceMin) { this.experienceMin = experienceMin; }

    public Integer getExperienceMax() { return experienceMax; }
    public void setExperienceMax(Integer experienceMax) { this.experienceMax = experienceMax; }

    public BigDecimal getSalaryMin() { return salaryMin; }
    public void setSalaryMin(BigDecimal salaryMin) { this.salaryMin = salaryMin; }

    public BigDecimal getSalaryMax() { return salaryMax; }
    public void setSalaryMax(BigDecimal salaryMax) { this.salaryMax = salaryMax; }

    public BigDecimal getSalaryAvg() { return salaryAvg; }
    public void setSalaryAvg(BigDecimal salaryAvg) { this.salaryAvg = salaryAvg; }

    public String getSkillTags() { return skillTags; }
    public void setSkillTags(String skillTags) { this.skillTags = skillTags; }

    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }

    public String getSourceSite() { return sourceSite; }
    public void setSourceSite(String sourceSite) { this.sourceSite = sourceSite; }

    public LocalDateTime getEtlTime() { return etlTime; }
    public void setEtlTime(LocalDateTime etlTime) { this.etlTime = etlTime; }
}
