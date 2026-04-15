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
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.mapper.AnalysisReportMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Open API", description = "Public read-only APIs for jobs, analysis, reports, and API keys")
@RestController
@RequestMapping("/api/v1/open")
@RequiredArgsConstructor
public class OpenApiController {

    private final JobPostingMapper jobMapper;
    private final AnalysisReportMapper reportMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyService apiKeyService;

    @Operation(summary = "Public jobs query")
    @GetMapping("/jobs")
    public R<?> openJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
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
        wrapper.orderByDesc(JobPosting::getPublishDate);

        IPage<JobPosting> result = jobMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(job -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", job.getId());
            item.put("title", job.getTitle());
            item.put("companyName", job.getCompanyName());
            item.put("city", job.getCity());
            item.put("industryName", job.getIndustryName());
            item.put("education", job.getEducation());
            item.put("experience", job.getExperience());
            item.put("salaryText", job.getSalaryText());
            item.put("publishDate", job.getPublishDate());
            return item;
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
    public R<?> openTrend(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry
    ) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> filters = new HashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);
        payload.put("filters", filters);
        payload.put("series", jobMapper.salaryTrend(city, industry));
        return R.ok(payload);
    }

    @Operation(summary = "Public report list")
    @GetMapping("/reports/public")
    public R<?> publicReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
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
        return R.page(result.getRecords(), result.getTotal(), safePage, safePageSize);
    }

    @Operation(summary = "Public report detail")
    @GetMapping("/reports/{id}")
    public R<?> publicReportDetail(@PathVariable Long id) {
        if (reportMapper == null) {
            return R.notFound("Report not found");
        }

        AnalysisReport report = reportMapper.selectById(id);
        if (report == null || report.getIsPublic() == null || report.getIsPublic() != 1) {
            return R.notFound("Report not found");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", report.getId());
        data.put("reportName", report.getReportName());
        data.put("reportType", report.getReportType());
        data.put("description", report.getDescription());
        data.put("analysisData", report.getAnalysisData());
        data.put("generatedAt", report.getGeneratedAt());
        data.put("viewCount", report.getViewCount());
        return R.ok(data);
    }

    @Operation(summary = "Create API Key")
    @PostMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> createApiKey(@RequestBody Map<String, Object> body) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String keyName = String.valueOf(body.getOrDefault("keyName", "Default API Key"));
        int qps = Integer.parseInt(String.valueOf(body.getOrDefault("rateLimitQps", 10)));
        int quota = Integer.parseInt(String.valueOf(body.getOrDefault("dailyQuota", 1000)));
        ApiKey key = apiKeyService.createApiKey(userId, keyName, qps, quota);
        return R.ok(key);
    }

    @Operation(summary = "List API Keys")
    @GetMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> listApiKeys() {
        return R.ok(apiKeyMapper.selectList(null));
    }
}
