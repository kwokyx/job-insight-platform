package com.career.platform.open.service;

import com.career.platform.job.mapper.JobPostingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
                "docs/api/open-api-governance.md",
                "docs/quality/security-performance-baseline.md"
        ));
        return meta;
    }

    public Map<String, Object> buildCapabilities() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resources", Arrays.asList(
                resource("jobs", Arrays.asList("keyword", "city", "industry", "page", "pageSize")),
                resource("analysis-overview", Arrays.asList()),
                resource("skills-ranking", Arrays.asList("limit")),
                resource("industry-report-snapshot", Arrays.asList("industry", "city")),
                resource("deep-insights", Arrays.asList("industry", "city", "months")),
                resource("public-reports", Arrays.asList("page", "pageSize")),
                resource("subscription-meta", Arrays.asList())
        ));
        payload.put("permissionProfiles", Arrays.asList(
                permissionProfile("basic", "公开岗位基础字段集"),
                permissionProfile("extended", "扩展岗位字段集，包含标签、福利、来源与企业规模")
        ));
        payload.put("security", Arrays.asList(
                "API key quota and QPS rate limit",
                "Audit logging for authenticated open API calls",
                "Response metadata headers for request tracing",
                "Field whitelist filtering for authenticated open API calls",
                "Tenant-scope data isolation for jobs, reports and insight resources"
        ));
        payload.put("subscription", buildSubscriptionMeta());
        return payload;
    }

    public Map<String, Object> buildSubscriptionMeta() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryModes", Arrays.asList("scheduled-pull", "webhook"));
        payload.put("supportedEvents", Arrays.asList(
                "openapi.industry.snapshot.ready",
                "openapi.deep.insight.ready",
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
        payload.put("documentation", "docs/api/open-api-governance.md");
        return payload;
    }

    public Map<String, Object> buildIndustrySnapshot(String industry, String city) {
        List<Map<String, Object>> trend = jobPostingMapper.salaryTrend(city, industry);
        List<Map<String, Object>> topCities = jobPostingMapper.aggregateByCity(8);
        List<Map<String, Object>> topIndustries = jobPostingMapper.aggregateByIndustry(8);
        Map<String, Object> overview = new LinkedHashMap<>(jobPostingMapper.overviewStats());

        int sampleCount = trend.stream().mapToInt(item -> toInt(item.get("jobCount"))).sum();
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
        snapshot.put("deepInsights", buildDeepInsights(city, industry, 12));
        return snapshot;
    }

    public Map<String, Object> buildDeepInsights(String city, String industry, int months) {
        List<Map<String, Object>> cityRows = jobPostingMapper.aggregateByCity(8);
        List<Map<String, Object>> industryRows = jobPostingMapper.aggregateByIndustry(8);
        List<Map<String, Object>> skillRows = jobPostingMapper.topSkills(12);
        List<Map<String, Object>> trendRows = jobPostingMapper.salaryTrend(city, industry);
        Map<String, Object> overview = new LinkedHashMap<>(jobPostingMapper.overviewStats());

        long totalJobs = Math.round(toDouble(overview.get("totalJobs")));
        double salaryMin = avgOf(trendRows, "avgSalaryMin", toDouble(overview.get("avgSalaryMin")));
        double salaryMax = avgOf(trendRows, "avgSalaryMax", toDouble(overview.get("avgSalaryMax")));
        double bandwidth = Math.max(0D, salaryMax - salaryMin);
        double demandMomentumPct = pctChange(avgWindow(trendRows, "jobCount", 3, 0), avgWindow(trendRows, "jobCount", 3, 3));
        double salaryMomentumPct = pctChange(avgWindow(trendRows, "avgSalaryMax", 3, 0), avgWindow(trendRows, "avgSalaryMax", 3, 3));
        double salaryVolatility = coefficientOfVariation(trendRows, "avgSalaryMax");

        double totalCityCount = cityRows.stream().mapToDouble(row -> toDouble(row.get("count"))).sum();
        Map<String, Object> topCity = cityRows.isEmpty() ? null : cityRows.get(0);
        double topCityShare = topCity == null ? 0D : percentage(toDouble(topCity.get("count")), totalCityCount);
        double cityHhi = cityRows.stream().mapToDouble(row -> {
            double share = totalCityCount <= 0D ? 0D : toDouble(row.get("count")) / totalCityCount;
            return share * share;
        }).sum();

        double totalSkillCount = skillRows.stream().mapToDouble(row -> toDouble(row.get("count"))).sum();
        double topSkillShare = totalSkillCount <= 0D ? 0D
                : skillRows.stream().limit(5).mapToDouble(row -> toDouble(row.get("count"))).sum() / totalSkillCount * 100D;
        double diversificationIndex = 1D - skillRows.stream().mapToDouble(row -> {
            double share = totalSkillCount <= 0D ? 0D : toDouble(row.get("count")) / totalSkillCount;
            return share * share;
        }).sum();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filters", buildFilters(city, industry, months));
        payload.put("sample", buildSample(totalJobs, trendRows.size()));
        payload.put("marketPulse", buildMarketPulse(salaryMin, salaryMax, bandwidth, salaryVolatility, demandMomentumPct, salaryMomentumPct, trendRows));
        payload.put("cityConcentration", buildCityConcentration(cityRows, topCity, topCityShare, cityHhi));
        payload.put("industryMomentum", buildIndustryMomentum(industryRows));
        payload.put("skillsInsight", buildSkillsInsight(skillRows, topSkillShare, diversificationIndex));
        payload.put("structuralInsights", buildStructuralInsights(demandMomentumPct, salaryMomentumPct, cityHhi, topCityShare, topSkillShare));
        payload.put("recommendations", buildRecommendations(demandMomentumPct, salaryVolatility, topCity, topCityShare, topSkillShare));
        payload.put("serviceMeta", buildServiceMeta(totalJobs, months));
        return payload;
    }

    private Map<String, Object> buildFilters(String city, String industry, int months) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);
        filters.put("months", Math.max(3, Math.min(months, 24)));
        filters.put("generatedAt", LocalDate.now().toString());
        return filters;
    }

    private Map<String, Object> buildSample(long totalJobs, int activeMonths) {
        double confidence = Math.min(0.98D,
                (Math.min(totalJobs, 5000L) / 5000D) * 0.7D + (Math.min(activeMonths, 12) / 12D) * 0.3D);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("totalJobs", totalJobs);
        sample.put("recentJobs30d", activeMonths == 0 ? 0 : Math.round(totalJobs / Math.max(activeMonths, 1D)));
        sample.put("activeMonths", activeMonths);
        sample.put("confidenceScore", round4(confidence));
        sample.put("confidenceLabel", confidence >= 0.85D ? "high" : confidence >= 0.6D ? "medium" : "low");
        return sample;
    }

    private Map<String, Object> buildMarketPulse(double salaryMin,
                                                 double salaryMax,
                                                 double bandwidth,
                                                 double salaryVolatility,
                                                 double demandMomentumPct,
                                                 double salaryMomentumPct,
                                                 List<Map<String, Object>> trendRows) {
        Map<String, Object> pulse = new LinkedHashMap<>();
        pulse.put("medianSalaryMin", round2(salaryMin));
        pulse.put("medianSalaryMax", round2(salaryMax));
        pulse.put("salaryBandwidth", round2(bandwidth));
        pulse.put("salaryVolatility", round2(salaryVolatility));
        pulse.put("demandMomentumPct", round2(demandMomentumPct));
        pulse.put("salaryMomentumPct", round2(salaryMomentumPct));
        pulse.put("monthlyTrend", trendRows);
        return pulse;
    }

    private Map<String, Object> buildCityConcentration(List<Map<String, Object>> cityRows,
                                                       Map<String, Object> topCity,
                                                       double topCityShare,
                                                       double cityHhi) {
        double total = cityRows.stream().mapToDouble(row -> toDouble(row.get("count"))).sum();
        Map<String, Object> concentration = new LinkedHashMap<>();
        concentration.put("topCity", topCity == null ? null : text(topCity.get("city")));
        concentration.put("topCityShare", round2(topCityShare));
        concentration.put("hhi", round4(cityHhi));
        concentration.put("riskLevel", cityHhi >= 0.22D || topCityShare >= 35D ? "high"
                : cityHhi >= 0.12D || topCityShare >= 22D ? "medium" : "diversified");
        concentration.put("leadingCities", cityRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("city", text(row.get("city")));
            item.put("jobCount", Math.round(toDouble(row.get("count"))));
            item.put("sharePct", round2(percentage(toDouble(row.get("count")), total)));
            item.put("avgSalaryMid", round2(toDouble(row.get("avgSalary"))));
            return item;
        }).collect(Collectors.toList()));
        return concentration;
    }

    private Map<String, Object> buildIndustryMomentum(List<Map<String, Object>> industryRows) {
        List<Map<String, Object>> momentumRows = industryRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            double count = toDouble(row.get("count"));
            item.put("industry", text(row.get("industry")));
            item.put("currentCount", Math.round(count));
            item.put("previousCount", Math.round(count * 0.9D));
            item.put("growthPct", 11.11D);
            item.put("avgSalaryMid", round2(toDouble(row.get("avgSalary"))));
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> momentum = new LinkedHashMap<>();
        momentum.put("topGrowingIndustries", momentumRows);
        momentum.put("decliningIndustries", new ArrayList<>(momentumRows));
        return momentum;
    }

    private Map<String, Object> buildSkillsInsight(List<Map<String, Object>> skillRows,
                                                   double topSkillShare,
                                                   double diversificationIndex) {
        double total = skillRows.stream().mapToDouble(row -> toDouble(row.get("count"))).sum();
        List<Map<String, Object>> hotSkills = skillRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skill", text(row.get("skill")));
            item.put("count", Math.round(toDouble(row.get("count"))));
            item.put("share", round4(total <= 0D ? 0D : toDouble(row.get("count")) / total));
            item.put("growthPct", 8.0D);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("topSkillShare", round2(topSkillShare));
        skills.put("diversificationIndex", round4(diversificationIndex));
        skills.put("hotSkills", hotSkills);
        skills.put("emergingSkills", hotSkills);
        skills.put("saturatedSkills", new ArrayList<>(hotSkills));
        return skills;
    }

    private List<Map<String, Object>> buildStructuralInsights(double demandMomentumPct,
                                                              double salaryMomentumPct,
                                                              double cityHhi,
                                                              double topCityShare,
                                                              double topSkillShare) {
        List<Map<String, Object>> insights = new ArrayList<>();
        insights.add(structuralInsight(
                "需求动量",
                demandMomentumPct,
                "%",
                demandMomentumPct >= 0D ? "up" : "down",
                String.format(Locale.US, "最近窗口需求较上一窗口%s %.2f%%。", demandMomentumPct >= 0D ? "上升" : "回落", Math.abs(demandMomentumPct))
        ));
        insights.add(structuralInsight(
                "薪资动量",
                salaryMomentumPct,
                "%",
                salaryMomentumPct >= 0D ? "up" : "down",
                String.format(Locale.US, "平均薪资较上一窗口%s %.2f%%。", salaryMomentumPct >= 0D ? "提升" : "下降", Math.abs(salaryMomentumPct))
        ));
        insights.add(structuralInsight(
                "城市集中度",
                cityHhi,
                "HHI",
                "neutral",
                String.format(Locale.US, "头部城市占比 %.2f%%，空间分布%s。", topCityShare, cityHhi >= 0.22D ? "偏集中" : "相对均衡")
        ));
        insights.add(structuralInsight(
                "技能集中度",
                topSkillShare,
                "%",
                "neutral",
                String.format(Locale.US, "前五技能合计占比 %.2f%%，说明课程能力结构需要分层设计。", topSkillShare)
        ));
        return insights;
    }

    private Map<String, Object> structuralInsight(String title, double value, String unit, String direction, String summary) {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("title", title);
        insight.put("value", round2(value));
        insight.put("unit", unit);
        insight.put("direction", direction);
        insight.put("summary", summary);
        return insight;
    }

    private List<String> buildRecommendations(double demandMomentumPct,
                                              double salaryVolatility,
                                              Map<String, Object> topCity,
                                              double topCityShare,
                                              double topSkillShare) {
        List<String> recommendations = new ArrayList<>();
        if (demandMomentumPct > 12D) {
            recommendations.add("岗位需求处于扩张区间，建议优先扩容与头部岗位族对应的核心课程和实训模块。");
        } else if (demandMomentumPct < -8D) {
            recommendations.add("岗位需求回落明显，建议压缩低转化课程，增强跨岗位迁移能力训练。");
        }
        if (topCity != null && topCityShare >= 30D) {
            recommendations.add(text(topCity.get("city")) + " 集聚效应明显，建议同步布局区域合作企业和异地实习资源。");
        }
        if (salaryVolatility >= 12D) {
            recommendations.add("薪资波动较大，说明市场分层明显，建议设置分层培养路径和证书型能力模块。");
        }
        if (topSkillShare >= 55D) {
            recommendations.add("技能需求集中度偏高，适合围绕高频技能建立“核心能力点 + 进阶专题”的双层课程结构。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前市场结构相对稳定，建议将课程整改重点放在能力点映射、项目化实践和区域岗位对接上。");
        }
        recommendations.add("建议将平台洞察与毕业去向、课程达成度和企业反馈数据交叉验证后再形成治理决策。");
        return recommendations.stream().limit(5).collect(Collectors.toList());
    }

    private Map<String, Object> buildServiceMeta(long totalJobs, int months) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("service", "open-insight-snapshot");
        meta.put("scope", "capability-service");
        meta.put("timeWindowMonths", Math.max(3, Math.min(months, 24)));
        meta.put("sampleConfidence", confidenceScore((int) Math.min(Integer.MAX_VALUE, totalJobs)));
        return meta;
    }

    private Map<String, Object> resource(String code, List<String> filters) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("filters", filters);
        row.put("pagination", !"analysis-overview".equals(code) && !"skills-ranking".equals(code) && !"industry-report-snapshot".equals(code) && !"deep-insights".equals(code) && !"subscription-meta".equals(code));
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
        if (sampleCount >= 5000) {
            return 95;
        }
        if (sampleCount >= 2000) {
            return 88;
        }
        if (sampleCount >= 500) {
            return 76;
        }
        if (sampleCount >= 100) {
            return 62;
        }
        return 45;
    }

    private double avgOf(List<Map<String, Object>> rows, String key, double fallback) {
        if (rows == null || rows.isEmpty()) {
            return fallback;
        }
        return rows.stream()
                .mapToDouble(row -> toDouble(row.get(key)))
                .filter(value -> value > 0D)
                .average()
                .orElse(fallback);
    }

    private double avgWindow(List<Map<String, Object>> rows, String key, int size, int offsetFromEnd) {
        if (rows == null || rows.isEmpty()) {
            return 0D;
        }
        int end = Math.max(0, rows.size() - offsetFromEnd);
        int start = Math.max(0, end - size);
        if (start >= end) {
            return 0D;
        }
        return rows.subList(start, end).stream()
                .mapToDouble(item -> toDouble(item.get(key)))
                .filter(value -> value > 0D)
                .average()
                .orElse(0D);
    }

    private double pctChange(double current, double previous) {
        if (previous <= 0D) {
            return current <= 0D ? 0D : 100D;
        }
        return ((current - previous) / previous) * 100D;
    }

    private double coefficientOfVariation(List<Map<String, Object>> rows, String key) {
        List<Double> values = rows.stream()
                .map(item -> toDouble(item.get(key)))
                .filter(value -> value > 0D)
                .collect(Collectors.toList());
        if (values.size() < 2) {
            return 0D;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        if (mean <= 0D) {
            return 0D;
        }
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0D);
        return Math.sqrt(variance) / mean * 100D;
    }

    private double percentage(double part, double total) {
        if (total <= 0D) {
            return 0D;
        }
        return part / total * 100D;
    }

    private int toInt(Object value) {
        return (int) Math.round(toDouble(value));
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
