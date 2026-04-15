package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数仓按日城市汇总 (DWS层)
 */
@Data
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
}
