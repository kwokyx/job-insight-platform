package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 数仓按月行业汇总 (DWS层)
 */
@TableName("dws_monthly_industry_summary")
public class DwsMonthlyIndustrySummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String statMonth;
    private String industry;
    private Integer jobCount;
    private BigDecimal avgSalaryMin;
    private BigDecimal avgSalaryMax;
    private BigDecimal growthRate;
    private String topSkills;   // JSON
    private LocalDateTime etlTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStatMonth() { return statMonth; }
    public void setStatMonth(String statMonth) { this.statMonth = statMonth; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public Integer getJobCount() { return jobCount; }
    public void setJobCount(Integer jobCount) { this.jobCount = jobCount; }

    public BigDecimal getAvgSalaryMin() { return avgSalaryMin; }
    public void setAvgSalaryMin(BigDecimal avgSalaryMin) { this.avgSalaryMin = avgSalaryMin; }

    public BigDecimal getAvgSalaryMax() { return avgSalaryMax; }
    public void setAvgSalaryMax(BigDecimal avgSalaryMax) { this.avgSalaryMax = avgSalaryMax; }

    public BigDecimal getGrowthRate() { return growthRate; }
    public void setGrowthRate(BigDecimal growthRate) { this.growthRate = growthRate; }

    public String getTopSkills() { return topSkills; }
    public void setTopSkills(String topSkills) { this.topSkills = topSkills; }

    public LocalDateTime getEtlTime() { return etlTime; }
    public void setEtlTime(LocalDateTime etlTime) { this.etlTime = etlTime; }
}
