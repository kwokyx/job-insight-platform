package com.career.platform.open.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.result.R;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.open.entity.ApiKey;
import com.career.platform.open.mapper.ApiKeyMapper;
import com.career.platform.open.service.ApiKeyService;
import com.career.platform.open.service.OpenApiGovernanceService;
import com.career.platform.open.service.OpenApiPermissionService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.service.SensitiveDataMaskingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Open API", description = "Public read-only APIs for jobs, analysis, reports, and API keys")
@RestController
@RequestMapping("/api/v1/open")
public class OpenApiController {

    private final JobPostingMapper jobMapper;
    private final AnalysisReportMapper reportMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyService apiKeyService;
    private final OpenApiGovernanceService openApiGovernanceService;
    private final OpenApiPermissionService openApiPermissionService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMaskingService sensitiveDataMaskingService;

    public OpenApiController(JobPostingMapper jobMapper, AnalysisReportMapper reportMapper,
                             ApiKeyMapper apiKeyMapper, ApiKeyService apiKeyService,
                             OpenApiGovernanceService openApiGovernanceService,
                             OpenApiPermissionService openApiPermissionService,
                             ObjectMapper objectMapper,
                             SensitiveDataMaskingService sensitiveDataMaskingService) {
        this.jobMapper = jobMapper;
        this.reportMapper = reportMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyService = apiKeyService;
        this.openApiGovernanceService = openApiGovernanceService;
        this.openApiPermissionService = openApiPermissionService;
        this.objectMapper = objectMapper;
        this.sensitiveDataMaskingService = sensitiveDataMaskingService;
    }

    @Operation(summary = "Open API meta")
    @GetMapping("/meta")
    public R<?> meta() {
        return R.ok(openApiGovernanceService.buildMeta());
    }

    @Operation(summary = "Open API capabilities")
    @GetMapping("/capabilities")
    public R<?> capabilities() {
        return R.ok(openApiGovernanceService.buildCapabilities());
    }

    @Operation(summary = "Open API subscription meta")
    @GetMapping("/subscriptions/meta")
    public R<?> subscriptionMeta() {
        return R.ok(openApiGovernanceService.buildSubscriptionMeta());
    }

    @Operation(summary = "Public jobs query")
    @GetMapping("/jobs")
    public R<?> openJobs(HttpServletRequest request,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String city,
                         @RequestParam(required = false) String industry,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);

        LambdaQueryWrapper<JobPosting> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(JobPosting::getTitle, keyword).or().like(JobPosting::getCompanyName, keyword));
        }
        if (StringUtils.hasText(city)) {
            wrapper.like(JobPosting::getCity, city);
        }
        if (StringUtils.hasText(industry)) {
            wrapper.like(JobPosting::getIndustryName, industry);
        }
        openApiPermissionService.applyJobTenantScope(wrapper, openApiPermissionService.resolveTenantScope(request));
        wrapper.orderByDesc(JobPosting::getPublishDate);

        List<String> allowedFields = openApiPermissionService.resolveJobFields(request);
        IPage<JobPosting> result = jobMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(job -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", job.getId());
            item.put("title", job.getTitle());
            item.put("companyName", job.getCompanyName());
            item.put("city", job.getCity());
            item.put("industryName", job.getIndustryName());
            item.put("education", job.getEducation());
            item.put("experience", job.getExperience());
            item.put("salaryText", job.getSalaryText());
            item.put("publishDate", job.getPublishDate());
            item.put("jobLabels", job.getJobLabels());
            item.put("jobBenefits", job.getJobBenefits());
            item.put("sourceSite", job.getSourceSite());
            item.put("companySize", job.getCompanySize());
            item.put("companyFinance", job.getCompanyFinance());
            return openApiPermissionService.filterFields(item, allowedFields);
        }).collect(Collectors.toList());

        return R.page(records, result.getTotal(), safePage, safePageSize);
    }

    @Operation(summary = "Public overview analysis")
    @GetMapping("/analysis/overview")
    public R<?> openOverview() {
        Map<String, Object> stats = new HashMap<>(jobMapper.overviewStats());
        stats.put("totalJobs", jobMapper.selectCount(null));
        stats.put("topCities", jobMapper.aggregateByCity(10));
        stats.put("topIndustries", jobMapper.aggregateByIndustry(10));
        stats.put("topSkills", jobMapper.topSkills(10));
        return R.ok(stats);
    }

    @Operation(summary = "Public skills ranking")
    @GetMapping("/analysis/skills")
    public R<?> openSkills(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return R.ok(jobMapper.topSkills(safeLimit));
    }

    @Operation(summary = "Public salary distribution")
    @GetMapping("/analysis/salary")
    public R<?> openSalary() {
        Map<String, Object> data = new HashMap<>();
        data.put("byCity", jobMapper.aggregateByCity(15));
        data.put("byIndustry", jobMapper.aggregateByIndustry(15));
        data.put("byEducation", jobMapper.aggregateByEducation());
        data.put("byExperience", jobMapper.aggregateByExperience());
        return R.ok(data);
    }

    @Operation(summary = "Public trend data")
    @GetMapping("/analysis/trend")
    public R<?> openTrend(@RequestParam(required = false) String city,
                          @RequestParam(required = false) String industry) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> filters = new HashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);
        payload.put("filters", filters);
        payload.put("series", jobMapper.salaryTrend(city, industry));
        return R.ok(payload);
    }

    @Operation(summary = "Industry analysis snapshot")
    @GetMapping("/analysis/industry")
    public R<?> industrySnapshot(@RequestParam(required = false) String industry,
                                 @RequestParam(required = false) String city) {
        return R.ok(openApiGovernanceService.buildIndustrySnapshot(industry, city));
    }

    @Operation(summary = "Public deep insight capability")
    @GetMapping("/analysis/insights")
    public R<?> deepInsightSnapshot(@RequestParam(required = false) String industry,
                                    @RequestParam(required = false) String city,
                                    @RequestParam(defaultValue = "12") int months) {
        return R.ok(openApiGovernanceService.buildDeepInsights(city, industry, months));
    }

    @Operation(summary = "Public report list")
    @GetMapping("/reports/public")
    public R<?> publicReports(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int pageSize) {
        return publicReportsInternal(null, page, pageSize);
    }

    @Operation(summary = "Public report list")
    @GetMapping("/reports/public-scoped")
    public R<?> publicReportsScoped(HttpServletRequest request,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        return publicReportsInternal(request, page, pageSize);
    }

    private R<?> publicReportsInternal(HttpServletRequest request, int page, int pageSize) {
        if (reportMapper == null) {
            return R.ok(new ArrayList<>());
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        IPage<AnalysisReport> result = reportMapper.selectPage(
                new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<AnalysisReport>()
                        .eq(AnalysisReport::getIsPublic, 1)
                        .orderByDesc(AnalysisReport::getGeneratedAt)
        );
        List<Map<String, Object>> records = new ArrayList<>();
        String tenantScope = request == null ? "public" : openApiPermissionService.resolveTenantScope(request);
        List<String> allowedReportFields = request == null
                ? java.util.Arrays.asList("id", "reportName", "reportType", "description", "generatedAt", "viewCount")
                : openApiPermissionService.resolveReportFields(request);
        for (AnalysisReport report : result.getRecords()) {
            Map<String, Object> data = parseAnalysisData(report.getAnalysisData());
            if (!openApiPermissionService.reportMatchesTenantScope(data, tenantScope)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", report.getId());
            item.put("reportName", report.getReportName());
            item.put("reportType", report.getReportType());
            item.put("description", report.getDescription());
            item.put("generatedAt", report.getGeneratedAt());
            item.put("viewCount", report.getViewCount());
            item.put("reportGovernance", data.getOrDefault("reportGovernance", new LinkedHashMap<>()));
            records.add(openApiPermissionService.filterFields(item, allowedReportFields));
        }
        return R.page(records, records.size(), safePage, safePageSize);
    }

    @Operation(summary = "Public report detail")
    @GetMapping("/reports/{id}")
    public R<?> publicReportDetail(HttpServletRequest request, @PathVariable Long id) {
        if (reportMapper == null) {
            return R.notFound("Report not found");
        }

        AnalysisReport report = reportMapper.selectById(id);
        if (report == null || report.getIsPublic() == null || report.getIsPublic() != 1) {
            return R.notFound("Report not found");
        }
        Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
        if (!openApiPermissionService.reportMatchesTenantScope(analysisData, openApiPermissionService.resolveTenantScope(request))) {
            return R.notFound("Report not found");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", report.getId());
        data.put("reportName", report.getReportName());
        data.put("reportType", report.getReportType());
        data.put("description", report.getDescription());
        data.put("analysisData", sensitiveDataMaskingService.maskReportData(analysisData));
        data.put("generatedAt", report.getGeneratedAt());
        data.put("viewCount", report.getViewCount());
        return R.ok(openApiPermissionService.filterFields(data, openApiPermissionService.resolveReportFields(request)));
    }

    @Operation(summary = "Create API Key")
    @PostMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> createApiKey(@RequestBody Map<String, Object> body) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String keyName = String.valueOf(body.getOrDefault("keyName", "Default API Key"));
        int qps = Math.max(1, Math.min(200, Integer.parseInt(String.valueOf(body.getOrDefault("rateLimitQps", 10)))));
        int quota = Math.max(100, Math.min(100000, Integer.parseInt(String.valueOf(body.getOrDefault("dailyQuota", 1000)))));
        String profile = String.valueOf(body.getOrDefault("permissionProfile", "basic"));
        String tenantScope = String.valueOf(body.getOrDefault("tenantScope", "public"));
        List<String> allowedJobFields = parseList(body.get("allowedJobFields"));
        String permissions = openApiPermissionService.buildPermissions(profile, tenantScope, allowedJobFields);
        ApiKey key = apiKeyService.createApiKey(userId, keyName, qps, quota, permissions);
        return R.ok(key);
    }

    @Operation(summary = "List API Keys")
    @GetMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> listApiKeys() {
        return R.ok(apiKeyMapper.selectList(null));
    }

    @Operation(summary = "Toggle API Key")
    @PutMapping("/api-keys/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> toggleApiKey(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean active = Boolean.parseBoolean(String.valueOf(body.getOrDefault("active", true)));
        return R.ok(apiKeyService.updateApiKeyStatus(id, active));
    }

    @Operation(summary = "List API call audit logs")
    @GetMapping("/api-keys/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> apiCallLogs(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> result = apiKeyService.listApiCallLogs(page, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        long total = ((Number) result.getOrDefault("total", 0)).longValue();
        int currentPage = ((Number) result.getOrDefault("page", page)).intValue();
        int currentPageSize = ((Number) result.getOrDefault("pageSize", pageSize)).intValue();
        return R.page(records, total, currentPage, currentPageSize);
    }

    private List<String> parseList(Object raw) {
        if (!(raw instanceof List)) {
            return new ArrayList<>();
        }
        List<?> source = (List<?>) raw;
        List<String> result = new ArrayList<>();
        for (Object item : source) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Map<String, Object> parseAnalysisData(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }
}
