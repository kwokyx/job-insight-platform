package com.career.platform.open.service;

import com.career.platform.job.mapper.JobPostingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenApiGovernanceService {

    private final JobPostingMapper jobPostingMapper;

    public OpenApiGovernanceService(JobPostingMapper jobPostingMapper) {
        this.jobPostingMapper = jobPostingMapper;
    }

    public Map<String, Object> buildMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("apiVersion", "v1");
        meta.put("serviceName", "career-platform-open-api");
        meta.put("defaultPageSize", 20);
        meta.put("maxPageSize", 50);
        meta.put("authModes", Arrays.asList("anonymous-read", "api-key"));
        meta.put("tenantMode", "declarative-scope");
        meta.put("fieldPermission", "field-whitelist");
        meta.put("audit", "sys_api_call_log");
        meta.put("subscriptionMode", Arrays.asList("scheduled-pull", "webhook"));
        meta.put("slaClass", "best-effort");
        meta.put("dataClassification", "employment-insight");
        meta.put("governanceDocs", Arrays.asList(
                "docs/open-api-governance.md",
                "docs/security-performance-baseline.md"
        ));
        return meta;
    }

    public Map<String, Object> buildCapabilities() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resources", Arrays.asList(
                resource("jobs", Arrays.asList("keyword", "city", "industry", "page", "pageSize")),
                resource("analysis-overview", Arrays.<String>asList()),
                resource("skills-ranking", Arrays.asList("limit")),
                resource("industry-report-snapshot", Arrays.asList("industry", "city")),
                resource("public-reports", Arrays.asList("page", "pageSize")),
                resource("subscription-meta", Arrays.<String>asList())
        ));
        payload.put("permissionProfiles", Arrays.asList(
                permissionProfile("basic", "公开岗位基础字段集"),
                permissionProfile("extended", "扩展岗位字段集，包含标签、福利、来源与企业规模")
        ));
        payload.put("security", Arrays.asList(
                "API key quota and QPS rate limit",
                "Audit logging for authenticated open API calls",
                "Response metadata headers for request tracing",
                "Field whitelist filtering for authenticated open API calls"
        ));
        payload.put("subscription", buildSubscriptionMeta());
        return payload;
    }

    public Map<String, Object> buildSubscriptionMeta() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryModes", Arrays.asList("scheduled-pull", "webhook"));
        payload.put("supportedEvents", Arrays.asList(
                "openapi.industry.snapshot.ready",
                "openapi.report.publication",
                "openapi.jobs.delta.available"
        ));
        payload.put("recommendedRetryPolicy", "3 attempts with exponential backoff");
        payload.put("signing", "HMAC-SHA256 with X-Signature and X-Timestamp headers");
        payload.put("payloadContract", Arrays.asList(
                field("eventType", "string", "事件类型"),
                field("requestId", "string", "请求追踪ID"),
                field("tenantScope", "string", "租户范围"),
                field("publishedAt", "string(datetime)", "事件发布时间"),
                field("data", "object", "事件数据体")
        ));
        payload.put("documentation", "docs/open-api-governance.md");
        return payload;
    }

    public Map<String, Object> buildIndustrySnapshot(String industry, String city) {
        List<Map<String, Object>> trend = jobPostingMapper.salaryTrend(city, industry);
        List<Map<String, Object>> topCities = jobPostingMapper.aggregateByCity(8);
        List<Map<String, Object>> topIndustries = jobPostingMapper.aggregateByIndustry(8);
        Map<String, Object> overview = new LinkedHashMap<>(jobPostingMapper.overviewStats());

        int sampleCount = trend.stream().mapToInt(item -> ((Number) item.getOrDefault("jobCount", 0)).intValue()).sum();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("industry", industry);
        snapshot.put("city", city);
        snapshot.put("period", LocalDate.now().toString());
        snapshot.put("sampleCount", sampleCount);
        snapshot.put("confidenceScore", confidenceScore(sampleCount));
        snapshot.put("salaryTrend", trend);
        snapshot.put("topCities", topCities);
        snapshot.put("topIndustries", topIndustries);
        snapshot.put("overview", overview);
        snapshot.put("comparison", buildComparison(trend));
        return snapshot;
    }

    private Map<String, Object> resource(String code, List<String> filters) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("filters", filters);
        row.put("pagination", true);
        return row;
    }

    private Map<String, Object> permissionProfile(String code, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("description", description);
        return row;
    }

    private Map<String, Object> field(String name, String type, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("type", type);
        row.put("description", description);
        return row;
    }

    private Map<String, Object> buildComparison(List<Map<String, Object>> trend) {
        Map<String, Object> comparison = new LinkedHashMap<>();
        if (trend.size() < 2) {
            comparison.put("momSalaryChange", null);
            comparison.put("trendDirection", "stable");
            return comparison;
        }
        Map<String, Object> current = trend.get(trend.size() - 1);
        Map<String, Object> previous = trend.get(trend.size() - 2);
        double currentMin = toDouble(current.get("avgSalaryMin"));
        double previousMin = toDouble(previous.get("avgSalaryMin"));
        Double change = previousMin <= 0 ? null : ((currentMin - previousMin) / previousMin) * 100;
        comparison.put("momSalaryChange", change);
        comparison.put("trendDirection", change == null ? "stable" : change > 3 ? "up" : change < -3 ? "down" : "stable");
        return comparison;
    }

    private int confidenceScore(int sampleCount) {
        if (sampleCount >= 5000) return 95;
        if (sampleCount >= 2000) return 88;
        if (sampleCount >= 500) return 76;
        if (sampleCount >= 100) return 62;
        return 45;
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? 0D : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }
}
