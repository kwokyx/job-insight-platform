package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数仓看板KPI快照 (ADS层)
 */
@TableName("ads_dashboard_kpi")
public class AdsDashboardKpi {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;
    private Long totalJobs;
    private Long activeJobs;
    private Long totalCompanies;
    private BigDecimal avgSalary;
    private BigDecimal medianSalary;
    private String topCity;
    private String topIndustry;
    private String topSkill;
    private Integer newJobs7d;
    private String salaryTrend;     // UP / DOWN / FLAT
    private LocalDateTime etlTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public Long getTotalJobs() { return totalJobs; }
    public void setTotalJobs(Long totalJobs) { this.totalJobs = totalJobs; }

    public Long getActiveJobs() { return activeJobs; }
    public void setActiveJobs(Long activeJobs) { this.activeJobs = activeJobs; }

    public Long getTotalCompanies() { return totalCompanies; }
    public void setTotalCompanies(Long totalCompanies) { this.totalCompanies = totalCompanies; }

    public BigDecimal getAvgSalary() { return avgSalary; }
    public void setAvgSalary(BigDecimal avgSalary) { this.avgSalary = avgSalary; }

    public BigDecimal getMedianSalary() { return medianSalary; }
    public void setMedianSalary(BigDecimal medianSalary) { this.medianSalary = medianSalary; }

    public String getTopCity() { return topCity; }
    public void setTopCity(String topCity) { this.topCity = topCity; }

    public String getTopIndustry() { return topIndustry; }
    public void setTopIndustry(String topIndustry) { this.topIndustry = topIndustry; }

    public String getTopSkill() { return topSkill; }
    public void setTopSkill(String topSkill) { this.topSkill = topSkill; }

    public Integer getNewJobs7d() { return newJobs7d; }
    public void setNewJobs7d(Integer newJobs7d) { this.newJobs7d = newJobs7d; }

    public String getSalaryTrend() { return salaryTrend; }
    public void setSalaryTrend(String salaryTrend) { this.salaryTrend = salaryTrend; }

    public LocalDateTime getEtlTime() { return etlTime; }
    public void setEtlTime(LocalDateTime etlTime) { this.etlTime = etlTime; }
}
