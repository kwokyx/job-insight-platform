package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数仓看板KPI快照 (ADS层)
 */
@Data
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
}
