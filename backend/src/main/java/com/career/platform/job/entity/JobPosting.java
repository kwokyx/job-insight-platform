package com.career.platform.job.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 职位数据实体
 */
@TableName("biz_job_posting")
public class JobPosting {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("job_id_source")
    private String jobIdSource;
    private String title;

    @TableField("company_id")
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

    @TableField("education_need")
    private String education;

    @TableField("experience_year")
    private String experience;

    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    @TableField("salary_raw")
    private String salaryText;

    @TableField("job_benefits")
    private String jobBenefits;

    @TableField("job_labels")
    private String jobLabels;

    @TableField("description")
    private String description;

    @TableField("source_url")
    private String sourceUrl;

    private String sourceSite;
    private String companySize;
    private String companyFinance;
    private LocalDate publishDate;
    private LocalDateTime crawlTime;

    private LocalDateTime crawlUpdateTime;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobIdSource() { return jobIdSource; }
    public void setJobIdSource(String jobIdSource) { this.jobIdSource = jobIdSource; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }

    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }

    public Long getJobCategoryId() { return jobCategoryId; }
    public void setJobCategoryId(Long jobCategoryId) { this.jobCategoryId = jobCategoryId; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getIndustryName() { return industryName; }
    public void setIndustryName(String industryName) { this.industryName = industryName; }

    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public BigDecimal getSalaryMin() { return salaryMin; }
    public void setSalaryMin(BigDecimal salaryMin) { this.salaryMin = salaryMin; }

    public BigDecimal getSalaryMax() { return salaryMax; }
    public void setSalaryMax(BigDecimal salaryMax) { this.salaryMax = salaryMax; }

    public String getSalaryText() { return salaryText; }
    public void setSalaryText(String salaryText) { this.salaryText = salaryText; }

    public String getJobBenefits() { return jobBenefits; }
    public void setJobBenefits(String jobBenefits) { this.jobBenefits = jobBenefits; }

    public String getJobLabels() { return jobLabels; }
    public void setJobLabels(String jobLabels) { this.jobLabels = jobLabels; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getSourceSite() { return sourceSite; }
    public void setSourceSite(String sourceSite) { this.sourceSite = sourceSite; }

    public String getCompanySize() { return companySize; }
    public void setCompanySize(String companySize) { this.companySize = companySize; }

    public String getCompanyFinance() { return companyFinance; }
    public void setCompanyFinance(String companyFinance) { this.companyFinance = companyFinance; }

    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }

    public LocalDateTime getCrawlTime() { return crawlTime; }
    public void setCrawlTime(LocalDateTime crawlTime) { this.crawlTime = crawlTime; }

    public LocalDateTime getCrawlUpdateTime() { return crawlUpdateTime; }
    public void setCrawlUpdateTime(LocalDateTime crawlUpdateTime) { this.crawlUpdateTime = crawlUpdateTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
