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
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.service.SensitiveDataMaskingService;
import com.career.platform.snapshot.service.PageSnapshotService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MarketSkillService marketSkillService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMaskingService sensitiveDataMaskingService;
    private final PageSnapshotService pageSnapshotService;

    @Autowired
    public OpenApiController(JobPostingMapper jobMapper, AnalysisReportMapper reportMapper,
                             ApiKeyMapper apiKeyMapper, ApiKeyService apiKeyService,
                             OpenApiGovernanceService openApiGovernanceService,
                             OpenApiPermissionService openApiPermissionService,
                             MarketSkillService marketSkillService,
                             ObjectMapper objectMapper,
                             SensitiveDataMaskingService sensitiveDataMaskingService,
                             PageSnapshotService pageSnapshotService) {
        this.jobMapper = jobMapper;
        this.reportMapper = reportMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyService = apiKeyService;
        this.openApiGovernanceService = openApiGovernanceService;
        this.openApiPermissionService = openApiPermissionService;
        this.marketSkillService = marketSkillService;
        this.objectMapper = objectMapper;
        this.sensitiveDataMaskingService = sensitiveDataMaskingService;
        this.pageSnapshotService = pageSnapshotService;
    }

    public OpenApiController(JobPostingMapper jobMapper, AnalysisReportMapper reportMapper,
                             ApiKeyMapper apiKeyMapper, ApiKeyService apiKeyService,
                             OpenApiGovernanceService openApiGovernanceService,
                             OpenApiPermissionService openApiPermissionService,
                             ObjectMapper objectMapper,
                             SensitiveDataMaskingService sensitiveDataMaskingService) {
        this(jobMapper, reportMapper, apiKeyMapper, apiKeyService, openApiGovernanceService,
                openApiPermissionService, new MarketSkillService(jobMapper), objectMapper, sensitiveDataMaskingService, null);
    }

    public OpenApiController(JobPostingMapper jobMapper, AnalysisReportMapper reportMapper,
                             ApiKeyMapper apiKeyMapper, ApiKeyService apiKeyService,
                             OpenApiGovernanceService openApiGovernanceService,
                             OpenApiPermissionService openApiPermissionService,
                             MarketSkillService marketSkillService,
                             ObjectMapper objectMapper,
                             SensitiveDataMaskingService sensitiveDataMaskingService) {
        this(jobMapper, reportMapper, apiKeyMapper, apiKeyService, openApiGovernanceService,
                openApiPermissionService, marketSkillService, objectMapper, sensitiveDataMaskingService, null);
    }

    @Operation(summary = "Open API meta")
    @GetMapping("/meta")
    public R<?> meta() {
        return R.ok(openApiGovernanceService.buildMeta());
    }

    @Operation(summary = "Open API health and endpoint catalog")
    @GetMapping("/health")
    public R<?> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("service", "career-platform-open-api");
        payload.put("version", "v1");
        payload.put("timestamp", java.time.LocalDateTime.now().toString());

        List<Map<String, Object>> endpoints = new ArrayList<>();
        endpoints.add(endpointDoc("GET", "/api/v1/open/health", "服务健康检查与端点目录", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/docs", "API 完整文档", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/meta", "API 元信息", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/capabilities", "API 能力声明", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/subscriptions/meta", "订阅机制元信息", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/jobs?keyword=&city=&industry=&page=1&pageSize=20", "岗位数据查询", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/analysis/overview", "市场总览分析", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/analysis/skills?limit=20", "技能排行", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/analysis/salary", "薪资分布分析", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/analysis/trend?city=&industry=", "薪资趋势", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/analysis/industry?industry=&city=", "行业快照", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/analysis/insights?industry=&city=&months=12", "深度洞察", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/reports/public?page=1&pageSize=10", "公开报告列表", false));
        endpoints.add(endpointDoc("GET", "/api/v1/open/reports/{id}", "公开报告详情", false));
        endpoints.add(endpointDoc("POST", "/api/v1/open/api-keys", "创建 API Key", true));
        endpoints.add(endpointDoc("GET", "/api/v1/open/api-keys", "列出 API Keys", true));
        endpoints.add(endpointDoc("PUT", "/api/v1/open/api-keys/{id}/toggle", "启用/禁用 API Key", true));
        endpoints.add(endpointDoc("GET", "/api/v1/open/api-keys/logs?page=1&pageSize=20", "审计日志", true));
        payload.put("endpoints", endpoints);

        Map<String, Object> authentication = new LinkedHashMap<>();
        authentication.put("anonymous", "所有 GET 端点支持匿名访问，返回公开数据");
        authentication.put("apiKey", "通过 X-API-Key 请求头传递 API Key，获得增强字段和配额追踪");
        authentication.put("admin", "POST/PUT 端点需要 JWT Bearer Token + ADMIN 角色");
        payload.put("authentication", authentication);
        return R.ok(payload);
    }

    @Operation(summary = "Open API complete documentation")
    @GetMapping("/docs")
    public R<?> docs() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "职业能力大数据平台 Open API 文档");
        doc.put("version", "v1");
        doc.put("baseUrl", "/api/v1/open");
        doc.put("swaggerUi", "/doc.html");
        doc.put("openApiSpec", "/v3/api-docs");

        Map<String, Object> auth = new LinkedHashMap<>();
        Map<String, Object> anonymousAuth = new LinkedHashMap<>();
        anonymousAuth.put("description", "无需任何认证即可访问所有 GET 端点");
        anonymousAuth.put("usage", "直接发起 HTTP GET 请求");
        auth.put("anonymous", anonymousAuth);

        Map<String, Object> apiKeyAuth = new LinkedHashMap<>();
        apiKeyAuth.put("description", "通过 API Key 获得增强的字段访问权限和调用审计");
        apiKeyAuth.put("header", "X-API-Key");
        apiKeyAuth.put("example", "X-API-Key: cpk_your_api_key_here");
        apiKeyAuth.put("rateLimit", "每秒 QPS 限流 + 每日调用配额");
        apiKeyAuth.put("responseHeaders", java.util.Arrays.asList(
                "X-RateLimit-Limit: 每日配额总量",
                "X-RateLimit-Remaining: 今日剩余配额",
                "X-Request-Id: 请求追踪 ID",
                "X-Tenant-Scope: 租户范围"
        ));
        auth.put("apiKey", apiKeyAuth);
        doc.put("authentication", auth);

        Map<String, Object> responseStructure = new LinkedHashMap<>();
        responseStructure.put("code", "200=成功, 401=未授权, 429=限流, 404=未找到");
        responseStructure.put("message", "状态描述");
        responseStructure.put("data", "业务数据");
        responseStructure.put("total", "分页总数 (仅分页接口)");
        responseStructure.put("page", "当前页码 (仅分页接口)");
        responseStructure.put("pageSize", "每页条数 (仅分页接口)");
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("structure", responseStructure);
        responseFormat.put("example", "{ \"code\": 200, \"message\": \"success\", \"data\": {...}, \"total\": 100, \"page\": 1, \"pageSize\": 20 }");
        doc.put("responseFormat", responseFormat);

        doc.put("meta", openApiGovernanceService.buildMeta());
        doc.put("capabilities", openApiGovernanceService.buildCapabilities());
        return R.ok(doc);
    }

    private Map<String, Object> endpointDoc(String method, String path, String description, boolean requiresAuth) {
        Map<String, Object> ep = new LinkedHashMap<>();
        ep.put("method", method);
        ep.put("path", path);
        ep.put("description", description);
        ep.put("authentication", requiresAuth ? "JWT Bearer (ADMIN)" : "匿名 / API Key (可选)");
        return ep;
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
        if (pageSnapshotService == null) {
            Map<String, Object> stats = new HashMap<>(jobMapper.overviewStats());
            stats.put("totalJobs", jobMapper.selectCount(null));
            stats.put("topCities", jobMapper.aggregateByCity(10));
            stats.put("topIndustries", jobMapper.aggregateByIndustry(10));
            stats.put("topSkills", marketSkillService.topSkills(10));
            return R.ok(stats);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(pageSnapshotService.getMarketOverview());
        snapshot.put("topSkills", marketSkillService.cleanSkillRows(pageSnapshotService.getMarketSkills(60), 10, MarketSkillService.TYPE_SKILL));
        return R.ok(snapshot);
    }

    @Operation(summary = "Public skills ranking")
    @GetMapping("/analysis/skills")
    public R<?> openSkills(@RequestParam(defaultValue = "20") int limit,
                           @RequestParam(defaultValue = "skill") String type) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        if (pageSnapshotService == null) {
            return R.ok(marketSkillService.cleanSkillRows(jobMapper.topSkills(Math.max(safeLimit * 4, 40)), safeLimit, type));
        }
        return R.ok(marketSkillService.cleanSkillRows(pageSnapshotService.getMarketSkills(Math.max(safeLimit * 3, 60)), safeLimit, type));
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
