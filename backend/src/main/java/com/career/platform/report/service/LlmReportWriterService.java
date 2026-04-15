package com.career.platform.report.service;

import com.career.platform.ai.client.LlmClient;
import com.career.platform.system.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmReportWriterService {

    @SuppressWarnings("unused")
    private final LlmClient llmClient;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    public Map<String, Object> generateNarrative(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        return buildFallbackNarrative(analysisData, reportType, userContext);
    }

    public String generateDiagnosticSummary(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Object summary = generateNarrative(analysisData, reportType, userContext).get("summary");
        return summary == null ? "" : String.valueOf(summary);
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
        String targetRole = stringValue(userContext.get("profileSummary"), "");
        String targetCity = stringValue(userContext.get("targetCityCode"), "");

        String topSkill = topValue(topSkills, "skill", "岗位核心技能");
        String secondSkill = secondValue(topSkills, "skill");
        String topCity = topValue(topCities, "city", "");
        String topIndustry = topValue(topIndustries, "industry", "");
        String mainstreamEducation = topValue(educationDist, "education", "");
        String mainstreamExperience = topValue(experienceDist, "experience", "");
        String sampleRole = topValue(jobSamples, "title", "代表岗位");
        String sampleSalary = topValue(jobSamples, "salaryText", avgSalaryMin + " - " + avgSalaryMax);
        String trendText = buildTrendText(salaryTrend);
        String keyGap = joinTopSkills(missingSkills, 3);

        String summary = buildRoleAwareSummary(
                reportType,
                roleType,
                totalJobs,
                avgSalaryMin,
                avgSalaryMax,
                completeness,
                alignment,
                topSkill,
                secondSkill,
                topCity,
                topIndustry,
                targetRole,
                targetCity,
                sampleRole,
                sampleSalary,
                trendText,
                keyGap,
                matchedSkillCount,
                marketSkillCount
        );

        List<String> chartInsights = buildChartInsights(
                roleType,
                totalJobs,
                avgSalaryMin,
                avgSalaryMax,
                completeness,
                alignment,
                topCity,
                topIndustry,
                mainstreamEducation,
                mainstreamExperience,
                trendText,
                sampleRole,
                sampleSalary,
                comparisonItems,
                missingSkills,
                matchedSkillCount,
                marketSkillCount
        );

        List<String> recommendations = buildRecommendations(
                roleType,
                targetRole,
                targetCity,
                completeness,
                alignment,
                topSkill,
                secondSkill,
                topCity,
                topIndustry,
                mainstreamEducation,
                mainstreamExperience,
                keyGap,
                jobSamples,
                risks
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("chartInsights", chartInsights);
        result.put("recommendations", recommendations);
        return result;
    }

    private String buildRoleAwareSummary(
            String reportType,
            Integer roleType,
            String totalJobs,
            String avgSalaryMin,
            String avgSalaryMax,
            int completeness,
            int alignment,
            String topSkill,
            String secondSkill,
            String topCity,
            String topIndustry,
            String targetRole,
            String targetCity,
            String sampleRole,
            String sampleSalary,
            String trendText,
            String keyGap,
            int matchedSkillCount,
            int marketSkillCount
    ) {
        String skillText = topSkill + (StringUtils.hasText(secondSkill) ? "、" + secondSkill : "");
        String marketAnchor = buildMarketAnchor(topCity, topIndustry);
        String gapText = StringUtils.hasText(keyGap) ? keyGap : "核心技能短板";

        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            return "本报告基于 " + totalJobs + " 条岗位样本生成，当前市场的主流薪资区间约为 "
                    + avgSalaryMin + " - " + avgSalaryMax + "，高频需求集中在 " + skillText + "。"
                    + marketAnchor + trendText
                    + "从教师与就业指导视角看，问题不在于缺少岗位，而在于学生训练成果尚未稳定转化为企业可识别的求职证据。"
                    + "当前画像完整度为 " + completeness + "%，市场匹配度为 " + alignment + "%，只覆盖了 "
                    + matchedSkillCount + "/" + marketSkillCount + " 项头部技能。"
                    + "像“" + sampleRole + "（" + sampleSalary + "）”这类样本岗位已经给出了清晰标准，后续重点应放在课程对齐、项目补齐和就业辅导联动。";
        }

        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            return "本报告基于 " + totalJobs + " 条岗位样本生成，当前平台所处市场的主流薪资区间约为 "
                    + avgSalaryMin + " - " + avgSalaryMax + "，高频需求主要集中在 " + skillText + "。"
                    + marketAnchor + trendText
                    + "从平台运营视角看，当前核心问题不是内容缺失，而是用户准备度与岗位高频需求之间仍存在明显错位。"
                    + "当前用户整体匹配度仅为 " + alignment + "%，头部技能覆盖度仅为 "
                    + matchedSkillCount + "/" + marketSkillCount + "，主要缺口集中在 " + gapText + "。"
                    + "因此平台需要优先把高频缺口转化为更直接的推荐、课程供给与运营触达动作。";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("本次 ").append(reportType).append(" 报告基于 ").append(totalJobs)
                .append(" 条岗位样本生成，当前市场平均薪资区间约为 ")
                .append(avgSalaryMin).append(" - ").append(avgSalaryMax)
                .append("，高频需求集中在 ").append(skillText).append("。")
                .append(marketAnchor).append(trendText);

        if (StringUtils.hasText(targetRole)) {
            summary.append("你的目标方向是 ").append(targetRole);
            if (StringUtils.hasText(targetCity)) {
                summary.append("，目标城市是 ").append(targetCity);
            }
            summary.append("。");
        } else {
            summary.append("你当前尚未明确目标岗位或目标城市，这会降低分析结果的针对性。");
        }

        summary.append("当前画像完整度为 ").append(completeness)
                .append("%，市场匹配度为 ").append(alignment)
                .append("%，头部技能覆盖度为 ").append(matchedSkillCount).append("/").append(marketSkillCount).append("。")
                .append("从代表岗位“").append(sampleRole).append("（").append(sampleSalary).append("）”可以看出，市场并不缺机会，真正的短板集中在 ")
                .append(gapText).append(" 等能力以及对应的项目证明。")
                .append("后续重点不应是继续盲投，而应先补齐关键短板、改写项目表达，再定向投递。");
        return summary.toString();
    }

    private List<String> buildChartInsights(
            Integer roleType,
            String totalJobs,
            String avgSalaryMin,
            String avgSalaryMax,
            int completeness,
            int alignment,
            String topCity,
            String topIndustry,
            String mainstreamEducation,
            String mainstreamExperience,
            String trendText,
            String sampleRole,
            String sampleSalary,
            List<Map<String, Object>> comparisonItems,
            List<Map<String, Object>> missingSkills,
            int matchedSkillCount,
            int marketSkillCount
    ) {
        List<String> insights = new ArrayList<>();
        insights.add("报告基于 " + totalJobs + " 条岗位样本，结论来自较大规模的真实岗位数据，而不是单个岗位噪声。");
        insights.add("当前市场平均薪资区间约为 " + avgSalaryMin + " - " + avgSalaryMax + "，而代表岗位“" + sampleRole + "”已可达到 " + sampleSalary + "，说明高质量岗位依然存在明确溢价。");

        if (StringUtils.hasText(topCity)) {
            insights.add("城市维度上，需求更集中在 " + topCity + " 等头部城市，投递与推荐策略应优先贴近这些高需求区域。");
        }
        if (StringUtils.hasText(topIndustry)) {
            insights.add("行业维度上，" + topIndustry + " 当前位于头部需求区间，适合作为案例对标、样本岗位选取和内容映射的核心来源。");
        }
        if (StringUtils.hasText(mainstreamEducation) || StringUtils.hasText(mainstreamExperience)) {
            insights.add("企业主流门槛主要落在 "
                    + defaultText(mainstreamEducation, "学历要求待补充")
                    + " 与 "
                    + defaultText(mainstreamExperience, "经验要求待补充")
                    + "，这意味着简历表达和训练内容需要主动贴合这些标准。");
        }
        if (StringUtils.hasText(trendText)) {
            insights.add(trendText);
        }

        insights.add("当前画像完整度为 " + completeness + "%，市场匹配度为 " + alignment + "%，说明现阶段的核心问题不是没有岗位，而是用户准备度仍未完全对齐岗位要求。");
        if (marketSkillCount > 0) {
            insights.add("在头部技能池中，当前只覆盖了 " + matchedSkillCount + "/" + marketSkillCount + " 项核心能力，技能结构与市场高频需求仍存在明显差距。");
        }
        if (!missingSkills.isEmpty()) {
            insights.add("最关键的能力缺口集中在 " + joinTopSkills(missingSkills, 3) + "，这些缺口会直接影响简历筛选通过率和岗位命中率。");
        }

        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            insights.add("对教师角色而言，问题的核心是教学输出与企业标准之间的转换效率偏低，课程成果还没有稳定转成就业证据。");
        } else if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            insights.add("对管理员角色而言，关键不只是看用户画像，而是把这些高频缺口持续转化为课程供给、推荐策略和分层运营动作。");
        } else if (!comparisonItems.isEmpty()) {
            insights.add("对比项显示，当前差距主要集中在画像完整度、技能覆盖度和市场匹配度三类指标，而不是单一薪资问题。");
        }

        return trimList(insights, 6);
    }

    private List<String> buildRecommendations(
            Integer roleType,
            String targetRole,
            String targetCity,
            int completeness,
            int alignment,
            String topSkill,
            String secondSkill,
            String topCity,
            String topIndustry,
            String mainstreamEducation,
            String mainstreamExperience,
            String keyGap,
            List<Map<String, Object>> jobSamples,
            List<String> risks
    ) {
        List<String> recommendations = new ArrayList<>();
        String skillPair = topSkill + (StringUtils.hasText(secondSkill) ? "、" + secondSkill : "");

        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            recommendations.add("先按学生匹配度和画像完整度做分层，把最需要干预的人群单独拉出来做课程补齐与就业辅导，不要平均用力。");
            recommendations.add("围绕 " + skillPair + " 重构训练任务，让课堂输出直接对应企业招聘中的高频技能要求。");
            recommendations.add("把课程作业升级为可展示项目，要求学生写清业务场景、技术动作和结果指标，减少“学过但证明不了”的问题。");
            recommendations.add("围绕主流门槛 " + defaultText(mainstreamEducation, "学历要求") + " 与 " + defaultText(mainstreamExperience, "经验要求") + " 重新审视训练标准，避免教学输出与岗位门槛错位。");
            recommendations.add("优先补齐 " + defaultText(keyGap, "关键技能缺口") + "，再安排简历优化、模拟面试和岗位筛选，形成闭环。");
            return trimList(recommendations, 5);
        }

        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            recommendations.add("把平台资源优先投向 " + skillPair + " 相关内容，因为这些能力最直接影响用户转化和岗位命中。");
            recommendations.add("对画像完整度低于 80% 且匹配度低于 60% 的用户建立自动触达策略，优先推送画像补全、技能补齐和岗位推荐。");
            recommendations.add("把高频缺口 " + defaultText(keyGap, "关键技能缺口") + " 直接映射到课程、专题页、智能推荐和运营活动，形成供给闭环。");
            if (StringUtils.hasText(topCity) || StringUtils.hasText(topIndustry)) {
                recommendations.add("围绕 " + defaultText(topCity, "头部城市") + " 与 " + defaultText(topIndustry, "头部行业方向") + " 建立专题内容和推荐位，提升平台供需对齐度。");
            }
            recommendations.add("持续追踪不同角色报告的改善幅度，用数据判断哪些模块真正带来转化，而不是只看访问量。");
            return trimList(recommendations, 5);
        }

        if (completeness < 80) {
            recommendations.add("先补全目标岗位、目标城市、技能、个人摘要和薪资预期；画像不完整会直接拉低推荐和报告精度。");
        }
        recommendations.add("接下来 2 到 4 周优先补齐 " + defaultText(keyGap, "最关键的技能缺口") + "，先拿下最影响岗位命中率的短板，不要同时铺太多技能线。");
        recommendations.add("把现有经历改写成与“" + defaultText(targetRole, "目标岗位") + "”直接相关的项目成果，重点写业务问题、你的动作和最终结果。");
        if (!jobSamples.isEmpty()) {
            recommendations.add("参照样本岗位“" + stringValue(jobSamples.get(0).get("title"), "代表岗位") + "”反推简历关键词和项目表达，让投递材料更贴近真实 JD。");
        }
        if (alignment < 60) {
            recommendations.add("在匹配度提升到 60% 之前，优先投递要求更接近当前能力层级的岗位，再逐步向更高要求岗位扩展。");
        }
        if (StringUtils.hasText(targetCity)) {
            recommendations.add("围绕目标城市“" + targetCity + "”单独维护一版岗位池和投递节奏，避免城市和岗位方向同时分散。");
        } else if (StringUtils.hasText(topCity)) {
            recommendations.add("如果目标城市还未确定，可以优先围绕需求更集中的 " + topCity + " 建立样本岗位池。");
        }
        if (StringUtils.hasText(topIndustry)) {
            recommendations.add("优先选择 " + topIndustry + " 相关岗位做样本对标，观察这类岗位对 " + topSkill + " 等能力的要求强度。");
        }
        if (!risks.isEmpty()) {
            recommendations.add("当前最需要优先处理的风险是：" + risks.get(0));
        }
        return trimList(recommendations, 5);
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
            return "最近薪资趋势波动较大，建议结合近 3 到 6 个月的连续数据判断，而不是只看单月峰值。";
        }
        return "最近薪资趋势显示，平均薪资下限变化约为 " + String.format(Locale.US, "%+.1f", change) + "%。";
    }

    private String buildMarketAnchor(String topCity, String topIndustry) {
        List<String> anchors = new ArrayList<>();
        if (StringUtils.hasText(topCity)) {
            anchors.add("需求更集中在 " + topCity);
        }
        if (StringUtils.hasText(topIndustry)) {
            anchors.add("岗位热点集中在 " + topIndustry);
        }
        if (anchors.isEmpty()) {
            return "";
        }
        return String.join("，", anchors) + "。";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return Collections.emptyList();
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

    private List<String> trimList(List<String> source, int limit) {
        List<String> result = new ArrayList<>();
        for (String item : source) {
            if (StringUtils.hasText(item) && !result.contains(item)) {
                result.add(item);
            }
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
            String value = stringValue(items.get(i).get("skill"), "");
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
        return stringValue(items.get(0).get(key), defaultValue);
    }

    private String secondValue(List<Map<String, Object>> items, String key) {
        if (items.size() < 2) {
            return "";
        }
        return stringValue(items.get(1).get(key), "");
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String formatSalary(Object value) {
        try {
            double number = Double.parseDouble(String.valueOf(value));
            if (number <= 0) {
                return "0K";
            }
            return String.format(Locale.US, "%.2fK", number / 1000.0D);
        } catch (Exception e) {
            return "0K";
        }
    }

    private String formatCount(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof Number) {
            return String.format(Locale.US, "%.0f", ((Number) value).doubleValue());
        }
        return String.valueOf(value);
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
}
