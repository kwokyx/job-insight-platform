package com.career.platform.analysis.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.warehouse.service.SupplyDemandService;
import com.career.platform.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "深度分析", description = "供需诊断、趋势预测、学历溢价、数仓ETL")
@RestController
@RequestMapping("/api/v1/analysis/deep")
public class DeepAnalysisController {

    private final SupplyDemandService supplyDemandService;
    private final WarehouseService warehouseService;
    private final JdbcTemplate jdbc;
    private final WebClient algorithmWebClient;

    public DeepAnalysisController(SupplyDemandService supplyDemandService, WarehouseService warehouseService,
                                  JdbcTemplate jdbc, @Qualifier("algorithmWebClient") WebClient algorithmWebClient) {
        this.supplyDemandService = supplyDemandService;
        this.warehouseService = warehouseService;
        this.jdbc = jdbc;
        this.algorithmWebClient = algorithmWebClient;
    }

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
                "SELECT COALESCE(experience, experience_year) AS experience, COUNT(*) AS jobCount, " +
                        "ROUND(AVG((IFNULL(salary_min,0)+IFNULL(salary_max,0))/2), 2) AS avgSalary " +
                        "FROM biz_job_posting " +
                        "WHERE COALESCE(experience, experience_year) IS NOT NULL " +
                        "  AND COALESCE(experience, experience_year) != '' " +
                        "  AND salary_min > 0 " +
                        "GROUP BY COALESCE(experience, experience_year) ORDER BY avgSalary DESC LIMIT 20"
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
            return R.ok(buildLocalTrendForecast(city, industry, months));
        }
    }

    @Log("手动触发ETL")
    @Operation(summary = "手动触发数仓ETL")
    @PostMapping("/etl/run")
    public R<?> triggerEtl() {
        try {
            warehouseService.runFullEtl();
            return R.ok(warehouseService.refreshPageSnapshots(null, "MANUAL_FULL_ETL"));
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "DEGRADED");
            result.put("message", "ETL execution is unavailable, returning current warehouse overview instead.");
            result.put("error", e.getMessage());
            result.put("overview", buildWarehouseOverviewSafe());
            return R.ok(result);
        }
    }

    @Operation(summary = "数仓数据概览")
    @GetMapping("/warehouse/overview")
    public R<?> warehouseOverview() {
        return R.ok(buildWarehouseOverviewSafe());
    }

    @Operation(summary = "页面快照刷新状态")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/snapshots/status")
    public R<?> snapshotStatus() {
        return R.ok(warehouseService.snapshotStatus());
    }

    public static class SnapshotRefreshRequest {
        private List<String> pageCodes;
        private Boolean runIncrementalEtl;

        public List<String> getPageCodes() {
            return pageCodes;
        }

        public void setPageCodes(List<String> pageCodes) {
            this.pageCodes = pageCodes;
        }

        public Boolean getRunIncrementalEtl() {
            return runIncrementalEtl;
        }

        public void setRunIncrementalEtl(Boolean runIncrementalEtl) {
            this.runIncrementalEtl = runIncrementalEtl;
        }
    }

    @Operation(summary = "手动刷新页面快照")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/snapshots/refresh")
    public R<?> refreshSnapshots(@RequestBody(required = false) SnapshotRefreshRequest request) {
        boolean runIncremental = request == null || request.getRunIncrementalEtl() == null || request.getRunIncrementalEtl();
        if (runIncremental) {
            warehouseService.runIncrementalEtl();
        }
        return R.ok(warehouseService.refreshPageSnapshots(
                request == null ? null : request.getPageCodes(),
                runIncremental ? "MANUAL_INCREMENTAL" : "MANUAL_SNAPSHOT_ONLY"
        ));
    }

    private String normalizeBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private Map<String, Object> buildLocalTrendForecast(String city, String industry, int months) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DATE_FORMAT(publish_date, '%Y-%m') AS period, " +
                        "ROUND(AVG(salary_min), 2) AS avgSalaryMin, " +
                        "ROUND(AVG(salary_max), 2) AS avgSalaryMax, " +
                        "COUNT(*) AS jobCount " +
                        "FROM biz_job_posting " +
                        "WHERE is_active = 1 AND publish_date IS NOT NULL " +
                        "AND (? IS NULL OR city = ?) " +
                        "AND (? IS NULL OR COALESCE(industry_name, job_classification) LIKE CONCAT('%', ?, '%')) " +
                        "GROUP BY DATE_FORMAT(publish_date, '%Y-%m') ORDER BY period DESC LIMIT 6",
                city, city, industry, industry
        );

        List<Map<String, Object>> forecast = new java.util.ArrayList<>();
        double lastMin = rows.isEmpty() ? 0D : readDouble(rows.get(0).get("avgSalaryMin"));
        double lastMax = rows.isEmpty() ? 0D : readDouble(rows.get(0).get("avgSalaryMax"));
        double lastCount = rows.isEmpty() ? 0D : readDouble(rows.get(0).get("jobCount"));

        for (int i = 1; i <= Math.max(1, months); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("period", LocalDateTime.now().plusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM")));
            item.put("avgSalaryMin", round2(lastMin * (1 + 0.01 * i)));
            item.put("avgSalaryMax", round2(lastMax * (1 + 0.012 * i)));
            item.put("jobCount", Math.round(lastCount * (1 + 0.015 * i)));
            forecast.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "local-fallback");
        result.put("city", city);
        result.put("industry", industry);
        result.put("history", rows);
        result.put("forecast", forecast);
        return result;
    }

    private Map<String, Object> buildWarehouseOverviewSafe() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dwdJobFact", safeCount("SELECT COUNT(*) FROM dwd_job_fact"));
        data.put("dwsDailyCitySummary", safeCount("SELECT COUNT(*) FROM dws_daily_city_summary"));
        data.put("dwsMonthlyIndustrySummary", safeCount("SELECT COUNT(*) FROM dws_monthly_industry_summary"));
        data.put("adsDashboardKpi", safeCount("SELECT COUNT(*) FROM ads_dashboard_kpi"));
        data.put("source", "warehouse");
        return data;
    }

    private Long safeCount(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double readDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0D;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
