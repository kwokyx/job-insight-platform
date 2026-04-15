package com.career.platform.analysis.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.warehouse.service.SupplyDemandService;
import com.career.platform.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "深度分析", description = "供需诊断、趋势预测、学历溢价、数仓ETL")
@RestController
@RequestMapping("/api/v1/analysis/deep")
@RequiredArgsConstructor
public class DeepAnalysisController {

    private final SupplyDemandService supplyDemandService;
    private final WarehouseService warehouseService;
    private final JdbcTemplate jdbc;
    @Qualifier("algorithmWebClient")
    private final WebClient algorithmWebClient;

    @Log("供需诊断")
    @Operation(summary = "供需剪刀差诊断（课程 vs 市场需求）")
    @GetMapping("/supply-demand")
    public R<?> supplyDemandGap(@RequestParam(required = false) String major) {
        return R.ok(supplyDemandService.analyzeSkyDemandGap(major));
    }

    @Log("供需诊断")
    @Operation(summary = "供需剪刀差诊断（POST）")
    @PostMapping("/supply-demand")
    public R<?> supplyDemandGapPost(@RequestBody(required = false) Map<String, Object> body) {
        String major = body == null ? null : String.valueOf(body.getOrDefault("major", ""));
        return R.ok(supplyDemandService.analyzeSkyDemandGap(normalizeBlank(major)));
    }

    @Log("课程差距诊断")
    @Operation(summary = "教学大纲 vs 市场需求交叉对比")
    @PostMapping("/curriculum-gap")
    public R<?> curriculumGap(@RequestBody(required = false) Map<String, Object> body) {
        String major = body == null ? null : String.valueOf(body.getOrDefault("major", ""));
        return R.ok(supplyDemandService.analyzeCurriculumGap(normalizeBlank(major)));
    }

    @Operation(summary = "学历/经验溢价分析")
    @GetMapping("/salary-premium")
    public R<?> salaryPremium() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> eduPremium = jdbc.queryForList(
                "SELECT education_std AS education, COUNT(*) AS jobCount, " +
                        "ROUND(AVG(salary_avg), 2) AS avgSalary, ROUND(MAX(salary_avg), 2) AS maxSalary " +
                        "FROM dwd_job_fact WHERE education_std IS NOT NULL AND salary_avg > 0 " +
                        "GROUP BY education_std ORDER BY avgSalary DESC"
        );
        result.put("educationPremium", eduPremium);

        List<Map<String, Object>> expPremium = jdbc.queryForList(
                "SELECT experience AS experience, COUNT(*) AS jobCount, " +
                        "ROUND(AVG((IFNULL(salary_min,0)+IFNULL(salary_max,0))/2), 2) AS avgSalary " +
                        "FROM biz_job_posting WHERE experience IS NOT NULL AND salary_min > 0 " +
                        "GROUP BY experience ORDER BY avgSalary DESC LIMIT 20"
        );
        result.put("experiencePremium", expPremium);
        return R.ok(result);
    }

    @Log("趋势预测")
    @Operation(summary = "就业趋势预测")
    @GetMapping("/trend-forecast")
    public R<?> trendForecast(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "3") int months
    ) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("city", city);
            params.put("industry", industry);
            params.put("months", months);

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/trend/forecast")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.fail("Algorithm service unavailable: " + e.getMessage());
        }
    }

    @Log("手动触发ETL")
    @Operation(summary = "手动触发数仓ETL")
    @PostMapping("/etl/run")
    public R<?> triggerEtl() {
        try {
            warehouseService.runFullEtl();
            return R.ok("ETL execution completed");
        } catch (Exception e) {
            return R.fail("ETL execution failed: " + e.getMessage());
        }
    }

    @Operation(summary = "数仓数据概览")
    @GetMapping("/warehouse/overview")
    public R<?> warehouseOverview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dwdJobFact", jdbc.queryForObject("SELECT COUNT(*) FROM dwd_job_fact", Long.class));
        data.put("dwsDailyCitySummary", jdbc.queryForObject("SELECT COUNT(*) FROM dws_daily_city_summary", Long.class));
        data.put("dwsMonthlyIndustrySummary", jdbc.queryForObject("SELECT COUNT(*) FROM dws_monthly_industry_summary", Long.class));
        data.put("adsDashboardKpi", jdbc.queryForObject("SELECT COUNT(*) FROM ads_dashboard_kpi", Long.class));
        return R.ok(data);
    }

    private String normalizeBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
