package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数仓按日城市汇总 (DWS层)
 */
@TableName("dws_daily_city_summary")
public class DwsDailyCitySummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;
    private String city;
    private Integer jobCount;
    private BigDecimal avgSalaryMin;
    private BigDecimal avgSalaryMax;
    private Integer newJobsCount;
    private String topSkills;   // JSON
    private LocalDateTime etlTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Integer getJobCount() { return jobCount; }
    public void setJobCount(Integer jobCount) { this.jobCount = jobCount; }

    public BigDecimal getAvgSalaryMin() { return avgSalaryMin; }
    public void setAvgSalaryMin(BigDecimal avgSalaryMin) { this.avgSalaryMin = avgSalaryMin; }

    public BigDecimal getAvgSalaryMax() { return avgSalaryMax; }
    public void setAvgSalaryMax(BigDecimal avgSalaryMax) { this.avgSalaryMax = avgSalaryMax; }

    public Integer getNewJobsCount() { return newJobsCount; }
    public void setNewJobsCount(Integer newJobsCount) { this.newJobsCount = newJobsCount; }

    public String getTopSkills() { return topSkills; }
    public void setTopSkills(String topSkills) { this.topSkills = topSkills; }

    public LocalDateTime getEtlTime() { return etlTime; }
    public void setEtlTime(LocalDateTime etlTime) { this.etlTime = etlTime; }
}
