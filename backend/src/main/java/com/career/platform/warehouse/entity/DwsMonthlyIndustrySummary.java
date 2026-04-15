package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 数仓按月行业汇总 (DWS层)
 */
@Data
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
}
