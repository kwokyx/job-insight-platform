package com.career.platform.open.service;

import com.career.platform.open.entity.ApiKey;
import com.career.platform.job.entity.JobPosting;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpenApiPermissionService {

    private static final List<String> DEFAULT_JOB_FIELDS = Arrays.asList(
            "id", "title", "companyName", "city", "industryName", "education", "experience", "salaryText", "publishDate"
    );
    private static final List<String> EXTENDED_JOB_FIELDS = Arrays.asList(
            "id", "title", "companyName", "city", "industryName", "education", "experience", "salaryText",
            "publishDate", "jobLabels", "jobBenefits", "sourceSite", "companySize", "companyFinance"
    );
    private static final List<String> REPORT_FIELDS = Arrays.asList(
            "id", "reportName", "reportType", "description", "analysisData", "generatedAt", "viewCount"
    );

    private final ObjectMapper objectMapper;

    public OpenApiPermissionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String defaultPermissions() {
        return buildPermissions("basic", "public", DEFAULT_JOB_FIELDS);
    }

    public String buildPermissions(String profile, String tenantScope, List<String> requestedFields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", StringUtils.hasText(profile) ? profile.trim().toLowerCase() : "basic");
        payload.put("tenantScope", StringUtils.hasText(tenantScope) ? tenantScope.trim() : "public");
        payload.put("allowedJobFields", sanitizeFields(requestedFields,
                "extended".equalsIgnoreCase(profile) ? EXTENDED_JOB_FIELDS : DEFAULT_JOB_FIELDS,
                DEFAULT_JOB_FIELDS));
        payload.put("allowedReportFields", REPORT_FIELDS);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"profile\":\"basic\",\"tenantScope\":\"public\"}";
        }
    }

    public void attachPermissionContext(HttpServletRequest request, ApiKey apiKey) {
        PermissionContext context = parse(apiKey == null ? null : apiKey.getPermissions());
        request.setAttribute("openApiTenantScope", context.tenantScope);
        request.setAttribute("openApiAllowedJobFields", context.allowedJobFields);
        request.setAttribute("openApiAllowedReportFields", context.allowedReportFields);
    }

    public List<String> resolveJobFields(HttpServletRequest request) {
        Object value = request.getAttribute("openApiAllowedJobFields");
        return value instanceof List ? castStringList(value) : DEFAULT_JOB_FIELDS;
    }

    public List<String> resolveReportFields(HttpServletRequest request) {
        Object value = request.getAttribute("openApiAllowedReportFields");
        return value instanceof List ? castStringList(value) : REPORT_FIELDS;
    }

    public String resolveTenantScope(HttpServletRequest request) {
        Object value = request.getAttribute("openApiTenantScope");
        return value == null ? "public" : String.valueOf(value);
    }

    public void applyJobTenantScope(LambdaQueryWrapper<JobPosting> wrapper, String tenantScope) {
        TenantRule rule = parseTenantScope(tenantScope);
        if ("city".equals(rule.dimension)) {
            wrapper.and(w -> w.like(JobPosting::getCity, rule.value).or().like(JobPosting::getCityCode, rule.value));
            return;
        }
        if ("industry".equals(rule.dimension)) {
            wrapper.like(JobPosting::getIndustryName, rule.value);
            return;
        }
        if ("source".equals(rule.dimension)) {
            wrapper.like(JobPosting::getSourceSite, rule.value);
        }
    }

    public boolean reportMatchesTenantScope(Map<String, Object> reportData, String tenantScope) {
        TenantRule rule = parseTenantScope(tenantScope);
        if ("public".equals(rule.dimension)) {
            return true;
        }
        Map<String, Object> governance = safeMap(reportData.get("reportGovernance"));
        if ("city".equals(rule.dimension)) {
            return containsIgnoreCase(governance.get("cityFilter"), rule.value);
        }
        if ("industry".equals(rule.dimension)) {
            return containsIgnoreCase(governance.get("industryFilter"), rule.value);
        }
        if ("major".equals(rule.dimension)) {
            return containsIgnoreCase(governance.get("majorFilter"), rule.value);
        }
        return true;
    }

    public Map<String, Object> filterFields(Map<String, Object> source, List<String> allowedFields) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String field : allowedFields) {
            if (source.containsKey(field)) {
                filtered.put(field, source.get(field));
            }
        }
        return filtered;
    }

    private PermissionContext parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new PermissionContext("public", DEFAULT_JOB_FIELDS, REPORT_FIELDS);
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            String profile = payload.get("profile") == null ? "basic" : String.valueOf(payload.get("profile"));
            String tenantScope = payload.get("tenantScope") == null ? "public" : String.valueOf(payload.get("tenantScope"));
            List<String> allowedJobFields = sanitizeFields(asStringList(payload.get("allowedJobFields")),
                    "extended".equalsIgnoreCase(profile) ? EXTENDED_JOB_FIELDS : DEFAULT_JOB_FIELDS,
                    DEFAULT_JOB_FIELDS);
            List<String> allowedReportFields = sanitizeFields(asStringList(payload.get("allowedReportFields")), REPORT_FIELDS, REPORT_FIELDS);
            return new PermissionContext(tenantScope, allowedJobFields, allowedReportFields);
        } catch (Exception ignored) {
            return new PermissionContext("public", DEFAULT_JOB_FIELDS, REPORT_FIELDS);
        }
    }

    private List<String> sanitizeFields(List<String> requested, List<String> allowedUniverse, List<String> defaults) {
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        List<String> sanitized = requested.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .filter(allowedUniverse::contains)
                .collect(Collectors.toList());
        return sanitized.isEmpty() ? new ArrayList<>(defaults) : sanitized;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) value;
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private boolean containsIgnoreCase(Object raw, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        String text = raw == null ? "" : String.valueOf(raw);
        return text.toLowerCase().contains(expected.toLowerCase());
    }

    private TenantRule parseTenantScope(String raw) {
        if (!StringUtils.hasText(raw) || "public".equalsIgnoreCase(raw.trim())) {
            return new TenantRule("public", "");
        }
        String normalized = raw.trim();
        int idx = normalized.indexOf(':');
        if (idx <= 0 || idx >= normalized.length() - 1) {
            return new TenantRule("public", "");
        }
        String dimension = normalized.substring(0, idx).trim().toLowerCase();
        String value = normalized.substring(idx + 1).trim();
        if (!StringUtils.hasText(value)) {
            return new TenantRule("public", "");
        }
        if (!Arrays.asList("city", "industry", "source", "major").contains(dimension)) {
            return new TenantRule("public", "");
        }
        return new TenantRule(dimension, value);
    }

    private static class PermissionContext {
        private final String tenantScope;
        private final List<String> allowedJobFields;
        private final List<String> allowedReportFields;

        private PermissionContext(String tenantScope, List<String> allowedJobFields, List<String> allowedReportFields) {
            this.tenantScope = tenantScope;
            this.allowedJobFields = allowedJobFields;
            this.allowedReportFields = allowedReportFields;
        }
    }

    private static class TenantRule {
        private final String dimension;
        private final String value;

        private TenantRule(String dimension, String value) {
            this.dimension = dimension;
            this.value = value;
        }
    }
}
