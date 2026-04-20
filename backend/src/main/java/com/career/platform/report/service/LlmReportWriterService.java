package com.career.platform.report.service;

import com.career.platform.ai.client.LlmClient;
import com.career.platform.system.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LlmReportWriterService {

    private static final Logger log = LoggerFactory.getLogger(LlmReportWriterService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmReportWriterService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> generateNarrative(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Map<String, Object> fallback = buildFallbackNarrative(analysisData, reportType, userContext);
        if (!llmClient.isConfigured()) {
            return fallback;
        }

        try {
            String prompt = buildPrompt(analysisData, reportType, userContext, fallback);
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(message("user", prompt));
            String raw = llmClient.chat(
                    "You are a senior career analytics report writer. Return strict JSON with fields summary, chartInsights, recommendations. Answer in Chinese.",
                    messages,
                    1400,
                    90
            );
            String cleaned = sanitizeLlmJson(raw);
            if (!StringUtils.hasText(cleaned)) {
                return fallback;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(cleaned, Map.class);
            String summary = stringValue(parsed.get("summary"));
            List<String> chartInsights = toStringList(parsed.get("chartInsights"));
            List<String> recommendations = toStringList(parsed.get("recommendations"));
            if (!StringUtils.hasText(summary) || chartInsights.isEmpty() || recommendations.isEmpty()) {
                return fallback;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", summary.trim());
            result.put("chartInsights", trimDistinct(chartInsights, 6));
            result.put("recommendations", trimDistinct(recommendations, 5));
            return result;
        } catch (Exception e) {
            log.warn("External report narrative unavailable, fallback to local writer: {}", e.getMessage());
            return fallback;
        }
    }

    public String generateDiagnosticSummary(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Object summary = generateNarrative(analysisData, reportType, userContext).get("summary");
        return summary == null ? "" : String.valueOf(summary);
    }

    private String buildPrompt(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext,
            Map<String, Object> fallback
    ) throws Exception {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("reportType", reportType);
        compact.put("overview", analysisData.get("overview"));
        compact.put("roleTemplate", analysisData.get("roleTemplate"));
        compact.put("userContext", userContext);
        compact.put("comparisonItems", analysisData.get("comparisonItems"));
        compact.put("jobSamples", analysisData.get("jobSamples"));
        compact.put("topSkills", trimRows(asList(analysisData.get("topSkills")), 8));
        compact.put("topCities", trimRows(asList(analysisData.get("topCities")), 6));
        compact.put("topIndustries", trimRows(asList(analysisData.get("topIndustries")), 6));
        compact.put("salaryTrend", trimRows(asList(analysisData.get("salaryTrend")), 8));
        compact.put("educationDist", trimRows(asList(analysisData.get("educationDist")), 6));
        compact.put("experienceDist", trimRows(asList(analysisData.get("experienceDist")), 6));
        compact.put("fallbackSummary", fallback.get("summary"));
        compact.put("fallbackChartInsights", fallback.get("chartInsights"));
        compact.put("fallbackRecommendations", fallback.get("recommendations"));

        return "请基于下面的平台报告数据生成中文 JSON，字段必须是 summary、chartInsights、recommendations。\n"
                + "要求：\n"
                + "1. summary 180 到 320 字，必须面向最终用户，写清市场、差距和结论。\n"
                + "2. chartInsights 输出 4 到 6 条，每条都要引用趋势、分布、对比项或样本岗位。\n"
                + "3. recommendations 输出 4 到 5 条，每条都必须是可执行建议，不要空话。\n"
                + "4. 不要输出 markdown，不要解释，只输出 JSON。\n\n"
                + objectMapper.writeValueAsString(compact);
    }

    private Map<String, Object> buildFallbackNarrative(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Map<String, Object> overview = asMap(analysisData.get("overview"));
        Map<String, Object> advisory = asMap(analysisData.get("advisory"));
        Map<String, Object> roleTemplate = asMap(analysisData.get("roleTemplate"));
        List<Map<String, Object>> topSkills = asList(analysisData.get("topSkills"));
        List<Map<String, Object>> topCities = asList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asList(analysisData.get("topIndustries"));
        List<Map<String, Object>> salaryTrend = asList(analysisData.get("salaryTrend"));
        List<Map<String, Object>> educationDist = asList(analysisData.get("educationDist"));
        List<Map<String, Object>> experienceDist = asList(analysisData.get("experienceDist"));
        List<Map<String, Object>> missingSkills = asList(advisory.get("missingSkills"));
        List<Map<String, Object>> jobSamples = asList(analysisData.get("jobSamples"));
        List<Map<String, Object>> comparisonItems = asList(analysisData.get("comparisonItems"));
        List<String> risks = toStringList(advisory.get("risks"));

        String totalJobs = formatCount(overview.get("totalJobs"));
        String avgSalaryMin = formatSalary(overview.get("avgSalaryMin"));
        String avgSalaryMax = formatSalary(overview.get("avgSalaryMax"));
        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        int alignment = parseInt(advisory.get("marketAlignmentScore"));
        int matchedSkillCount = parseInt(advisory.get("matchedSkillCount"));
        int marketSkillCount = parseInt(advisory.get("marketSkillCount"));
        Integer roleType = parseNullableInt(roleTemplate.get("roleType"));

        String targetRole = stringValue(userContext.get("profileSummary"));
        String targetCity = stringValue(userContext.get("targetCityCode"));
        String topSkill = topValue(topSkills, "skill", "核心技能");
        String secondSkill = topValue(topSkills.size() > 1 ? Collections.singletonList(topSkills.get(1)) : Collections.emptyList(), "skill", "");
        String topCity = topValue(topCities, "city", "");
        String topIndustry = topValue(topIndustries, "industry", "");
        String mainstreamEducation = topValue(educationDist, "education", "");
        String mainstreamExperience = topValue(experienceDist, "experience", "");
        String sampleRole = topValue(jobSamples, "title", "代表岗位");
        String sampleSalary = topValue(jobSamples, "salaryText", avgSalaryMin + " - " + avgSalaryMax);
        String keyGap = joinTopSkills(missingSkills, 3);
        String trendText = buildTrendText(salaryTrend);

        String summary = buildSummary(
                reportType,
                roleType,
                totalJobs,
                avgSalaryMin,
                avgSalaryMax,
                topSkill,
                secondSkill,
                topCity,
                topIndustry,
                targetRole,
                targetCity,
                sampleRole,
                sampleSalary,
                completeness,
                alignment,
                matchedSkillCount,
                marketSkillCount,
                keyGap,
                trendText
        );

        List<String> chartInsights = buildChartInsights(
                roleType,
                totalJobs,
                avgSalaryMin,
                avgSalaryMax,
                topCity,
                topIndustry,
                mainstreamEducation,
                mainstreamExperience,
                sampleRole,
                sampleSalary,
                completeness,
                alignment,
                matchedSkillCount,
                marketSkillCount,
                comparisonItems,
                missingSkills,
                trendText
        );

        List<String> recommendations = buildRecommendations(
                roleType,
                targetRole,
                targetCity,
                topSkill,
                secondSkill,
                topCity,
                topIndustry,
                completeness,
                alignment,
                keyGap,
                mainstreamEducation,
                mainstreamExperience,
                risks,
                jobSamples
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("chartInsights", chartInsights);
        result.put("recommendations", recommendations);
        return result;
    }

    private String buildSummary(
            String reportType,
            Integer roleType,
            String totalJobs,
            String avgSalaryMin,
            String avgSalaryMax,
            String topSkill,
            String secondSkill,
            String topCity,
            String topIndustry,
            String targetRole,
            String targetCity,
            String sampleRole,
            String sampleSalary,
            int completeness,
            int alignment,
            int matchedSkillCount,
            int marketSkillCount,
            String keyGap,
            String trendText
    ) {
        String skillText = topSkill + (StringUtils.hasText(secondSkill) ? "、" + secondSkill : "");
        StringBuilder sb = new StringBuilder();
        sb.append("本次报告基于 ").append(totalJobs).append(" 条岗位样本生成，当前市场主流薪资区间约为 ")
                .append(avgSalaryMin).append(" - ").append(avgSalaryMax).append("，高频需求集中在 ")
                .append(skillText).append("。");
        if (StringUtils.hasText(topCity) || StringUtils.hasText(topIndustry)) {
            sb.append("机会更集中在");
            if (StringUtils.hasText(topCity)) {
                sb.append(topCity);
            }
            if (StringUtils.hasText(topIndustry)) {
                sb.append(StringUtils.hasText(topCity) ? " 的 " : "").append(topIndustry);
            }
            sb.append("方向。");
        }
        if (StringUtils.hasText(trendText)) {
            sb.append(trendText);
        }
        if (StringUtils.hasText(targetRole)) {
            sb.append("你当前关注的目标方向是 ").append(targetRole);
            if (StringUtils.hasText(targetCity)) {
                sb.append("，目标城市是 ").append(targetCity);
            }
            sb.append("。");
        }
        sb.append("当前画像完整度为 ").append(completeness).append("%，市场匹配度约为 ").append(alignment).append("%，")
                .append("头部技能覆盖数为 ").append(matchedSkillCount).append("/").append(marketSkillCount).append("。");
        sb.append("样本岗位“").append(sampleRole).append("（").append(sampleSalary).append("）”表明，市场并不缺机会，")
                .append("真正影响结果的是能力缺口、项目证明和投递针对性。");
        if (StringUtils.hasText(keyGap)) {
            sb.append("当前最需要优先补齐的缺口主要集中在 ").append(keyGap).append("。");
        }

        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            sb.append("从教师与就业指导视角看，后续重点不是继续堆叠概念性学习，而是把课程训练结果转成企业可识别的项目成果、能力证据和求职表达。");
        } else if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            sb.append("从管理员视角看，后续重点是把这些高频缺口映射成课程供给、推荐策略和运营触达动作，形成平台级闭环。");
        } else if ("SUPPLY_DEMAND".equalsIgnoreCase(reportType)) {
            sb.append("从供需视角看，平台后续最有价值的动作是持续跟踪紧缺技能、提高匹配度，并把高频缺口直接转成个人提升计划。");
        }
        return sb.toString();
    }

    private List<String> buildChartInsights(
            Integer roleType,
            String totalJobs,
            String avgSalaryMin,
            String avgSalaryMax,
            String topCity,
            String topIndustry,
            String mainstreamEducation,
            String mainstreamExperience,
            String sampleRole,
            String sampleSalary,
            int completeness,
            int alignment,
            int matchedSkillCount,
            int marketSkillCount,
            List<Map<String, Object>> comparisonItems,
            List<Map<String, Object>> missingSkills,
            String trendText
    ) {
        List<String> insights = new ArrayList<>();
        insights.add("报告基于 " + totalJobs + " 条岗位样本，结论来自真实招聘数据，而不是单一岗位噪声。");
        insights.add("当前市场平均薪资区间约为 " + avgSalaryMin + " - " + avgSalaryMax + "，代表岗位“" + sampleRole + "”可达到 " + sampleSalary + "。");
        if (StringUtils.hasText(topCity)) {
            insights.add("城市维度上，岗位需求更集中在 " + topCity + "，说明高价值机会仍明显向头部城市聚集。");
        }
        if (StringUtils.hasText(topIndustry)) {
            insights.add("岗位赛道上，" + topIndustry + " 当前位于头部需求区间，适合优先作为样本方向和项目对标来源。");
        }
        if (StringUtils.hasText(mainstreamEducation) || StringUtils.hasText(mainstreamExperience)) {
            insights.add("企业主流门槛更多落在 " + defaultText(mainstreamEducation, "学历要求待补充") + " 与 "
                    + defaultText(mainstreamExperience, "经验要求待补充") + "，简历表达和训练产出需要主动贴近这些标准。");
        }
        if (StringUtils.hasText(trendText)) {
            insights.add(trendText);
        }
        insights.add("当前画像完整度为 " + completeness + "%，市场匹配度约为 " + alignment + "%，问题核心不只是岗位数量，而是准备度是否对齐。");
        if (marketSkillCount > 0) {
            insights.add("在头部技能池中，当前仅覆盖 " + matchedSkillCount + "/" + marketSkillCount + " 项核心能力，结构性差距仍然明显。");
        }
        if (!missingSkills.isEmpty()) {
            insights.add("最关键的能力缺口集中在 " + joinTopSkills(missingSkills, 3) + "，这些缺口会直接影响岗位命中率和面试通过率。");
        }
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            insights.add("教师模板下更值得关注的是课程成果与企业标准之间的转译效率，而不只是学生是否接触过相关知识点。");
        } else if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            insights.add("管理员模板下更值得关注的是把高频缺口转成课程供给、推荐策略和分层运营，而不是只停留在静态统计。");
        } else if (!comparisonItems.isEmpty()) {
            insights.add("对比项显示当前差距主要集中在画像完整度、技能覆盖度和市场匹配度三类指标，而不是单一薪资问题。");
        }
        return trimDistinct(insights, 6);
    }

    private List<String> buildRecommendations(
            Integer roleType,
            String targetRole,
            String targetCity,
            String topSkill,
            String secondSkill,
            String topCity,
            String topIndustry,
            int completeness,
            int alignment,
            String keyGap,
            String mainstreamEducation,
            String mainstreamExperience,
            List<String> risks,
            List<Map<String, Object>> jobSamples
    ) {
        List<String> recommendations = new ArrayList<>();
        String skillPair = topSkill + (StringUtils.hasText(secondSkill) ? "、" + secondSkill : "");

        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            recommendations.add("优先按学生匹配度和画像完整度做分层，先干预最需要提升的群体，不要平均用力。");
            recommendations.add("围绕 " + skillPair + " 重构训练任务，让课程输出直接对应企业招聘中的高频能力要求。");
            recommendations.add("把课程作业升级为可展示项目，要求学生写清业务场景、技术动作和结果指标。");
            recommendations.add("围绕主流门槛 " + defaultText(mainstreamEducation, "学历要求") + " 与 " + defaultText(mainstreamExperience, "经验要求") + " 重新审视训练标准。");
            recommendations.add("优先补齐 " + defaultText(keyGap, "关键技能缺口") + "，再做简历优化、模拟面试和岗位筛选。");
            return trimDistinct(recommendations, 5);
        }

        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            recommendations.add("把平台资源优先投向 " + skillPair + " 相关内容，因为这些能力最直接影响用户转化和岗位命中。");
            recommendations.add("对画像完整度低于 80% 且匹配度低于 60% 的用户建立自动触达策略，优先推送画像补全、技能补齐和岗位推荐。");
            recommendations.add("把高频缺口 " + defaultText(keyGap, "关键技能缺口") + " 直接映射到课程、专题页、智能推荐和运营活动，形成平台闭环。");
            recommendations.add("围绕 " + defaultText(topCity, "头部城市") + " 与 " + defaultText(topIndustry, "头部赛道") + " 建立专题内容和推荐位，提高供需对齐度。");
            recommendations.add("持续跟踪各类报告触发后的提升幅度，用数据判断哪些模块真正带来转化。");
            return trimDistinct(recommendations, 5);
        }

        if (completeness < 80) {
            recommendations.add("先补全目标岗位、目标城市、技能、个人摘要和薪资预期，画像不完整会直接拉低推荐和报告精度。");
        }
        recommendations.add("接下来 2 到 4 周优先补齐 " + defaultText(keyGap, "最关键的技能缺口") + "，先解决最影响岗位命中率的短板。");
        recommendations.add("把现有经历改写成与“" + defaultText(targetRole, "目标岗位") + "”直接相关的项目成果，重点写业务问题、你的动作和结果。");
        if (!jobSamples.isEmpty()) {
            recommendations.add("参照样本岗位“" + stringValue(jobSamples.get(0).get("title")) + "”反推简历关键词和项目表达，让投递材料更贴近真实 JD。");
        }
        if (alignment < 60) {
            recommendations.add("在匹配度提升到 60% 之前，优先投递更贴近当前能力层级的岗位，再逐步向更高要求扩展。");
        }
        if (StringUtils.hasText(targetCity)) {
            recommendations.add("围绕目标城市“" + targetCity + "”维护单独的岗位池和投递节奏，避免城市和岗位方向同时分散。");
        } else if (StringUtils.hasText(topCity)) {
            recommendations.add("如果目标城市尚未确定，可优先围绕需求更集中的 " + topCity + " 建立样本岗位池。");
        }
        if (StringUtils.hasText(topIndustry)) {
            recommendations.add("优先选择 " + topIndustry + " 相关岗位做样本对标，观察它们对 " + topSkill + " 等能力的要求强度。");
        }
        if (!risks.isEmpty()) {
            recommendations.add("当前优先级最高的风险是：" + risks.get(0));
        }
        return trimDistinct(recommendations, 5);
    }

    private String buildTrendText(List<Map<String, Object>> salaryTrend) {
        List<Map<String, Object>> effective = new ArrayList<>();
        for (Map<String, Object> row : salaryTrend) {
            if (parseInt(row.get("jobCount")) >= 100) {
                effective.add(row);
            }
        }
        if (effective.size() < 2) {
            return "";
        }

        int start = Math.max(0, effective.size() - 6);
        double first = toDouble(effective.get(start).get("avgSalaryMin"));
        double last = toDouble(effective.get(effective.size() - 1).get("avgSalaryMin"));
        if (first <= 0 || last <= 0) {
            return "";
        }

        double change = (last - first) / first * 100;
        if (Math.abs(change) > 80D) {
            return "最近薪资波动较大，建议结合近 3 到 6 个月连续数据判断，而不要只看单月峰值。";
        }
        return "最近薪资趋势显示，平均薪资下限变化约为 " + String.format(Locale.US, "%+.1f", change) + "%。";
    }

    private List<Map<String, String>> trimRows(List<Map<String, Object>> rows, int limit) {
        List<Map<String, String>> result = new ArrayList<>();
        int max = Math.min(rows.size(), limit);
        for (int i = 0; i < max; i++) {
            Map<String, String> item = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rows.get(i).entrySet()) {
                item.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            result.add(item);
        }
        return result;
    }

    private String sanitizeLlmJson(String raw) {
        String value = stringValue(raw).trim();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        value = value.replaceAll("(?is)^.*?</think>", "").trim();
        value = value.replaceAll("(?is)^```json\\s*", "")
                .replaceAll("(?is)^```\\s*", "")
                .replaceAll("(?is)```$", "")
                .trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : "";
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private List<String> trimDistinct(List<String> source, int limit) {
        List<String> result = new ArrayList<>();
        for (String item : source) {
            if (!StringUtils.hasText(item) || result.contains(item)) {
                continue;
            }
            result.add(item.trim());
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Integer parseNullableInt(Object value) {
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int parseInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String joinTopSkills(List<Map<String, Object>> items, int limit) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < items.size() && i < limit; i++) {
            String value = stringValue(items.get(i).get("skill"));
            if (StringUtils.hasText(value)) {
                names.add(value);
            }
        }
        return names.isEmpty() ? "" : String.join("、", names);
    }

    private String topValue(List<Map<String, Object>> items, String key, String defaultValue) {
        if (items.isEmpty()) {
            return defaultValue;
        }
        return StringUtils.hasText(stringValue(items.get(0).get(key))) ? stringValue(items.get(0).get(key)) : defaultValue;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String formatSalary(Object value) {
        try {
            double number = Double.parseDouble(String.valueOf(value));
            return number <= 0 ? "0K" : String.format(Locale.US, "%.2fK", number);
        } catch (Exception e) {
            return "0K";
        }
    }

    private String formatCount(Object value) {
        if (value instanceof Number) {
            return String.format(Locale.US, "%,.0f", ((Number) value).doubleValue());
        }
        return value == null ? "0" : String.valueOf(value);
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
