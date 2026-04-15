package com.career.platform.report.service;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.subscription.entity.Notification;
import com.career.platform.subscription.mapper.NotificationMapper;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.SysUserMapper;
import com.career.platform.warehouse.service.SupplyDemandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final AnalysisReportMapper reportMapper;
    private final AnalysisTaskMapper taskMapper;
    private final JobPostingMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final LlmReportWriterService llmReportWriterService;
    private final SupplyDemandService supplyDemandService;
    private final NotificationMapper notificationMapper;
    private final UserInsightService userInsightService;
    private final MarketSkillService marketSkillService;
    private final SysUserMapper sysUserMapper;

    @Async("reportExecutor")
    public void executeReportGeneration(Long taskId, String reportType, String reportName, Long userId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Report task not found: {}", taskId);
            return;
        }

        try {
            updateTask(task, "RUNNING", 10, null);

            Map<String, Object> taskParams = parseTaskParams(task.getParams());
            Integer templateRoleType = resolveTemplateRoleType(userId, taskParams);
            String normalizedType = reportType == null ? "COMPREHENSIVE" : reportType.toUpperCase(Locale.ROOT);

            Map<String, Object> analysisData = new LinkedHashMap<>();
            Map<String, Object> userContext = userInsightService.loadUserContext(userId);
            Map<String, Object> advisory = userInsightService.buildPlatformAdvisory(userId);

            analysisData.put("overview", loadOverview());
            analysisData.put("roleTemplate", buildRoleTemplate(templateRoleType));
            analysisData.put("userContext", userContext);
            analysisData.put("advisory", advisory);

            enrichMarketData(analysisData, normalizedType, userContext);

            analysisData.put("targetAudience", determineTargetAudience(userContext, templateRoleType));
            analysisData.put("reportFocus", determineReportFocus(normalizedType, userContext, advisory, templateRoleType));
            analysisData.put("jobSamples", buildJobSamples(userContext));
            analysisData.put("comparisonItems", buildComparisonItems(userContext, advisory, analysisData, templateRoleType));
            analysisData.put("chartCards", buildChartCards(analysisData));

            task.setProgress(70);
            taskMapper.updateById(task);

            Map<String, Object> narrative = llmReportWriterService.generateNarrative(analysisData, normalizedType, userContext);
            String summary = sanitizeNarrativeText(stringValue(narrative.get("summary")));
            List<String> chartInsights = sanitizeNarrativeList(narrative.get("chartInsights"));
            List<String> recommendations = sanitizeNarrativeList(narrative.get("recommendations"));

            analysisData.put("diagnosticSummary", summary);
            analysisData.put("chartInsights", chartInsights);
            analysisData.put("recommendations", recommendations);
            analysisData.put("actionPlan", buildActionPlan(analysisData, userContext, advisory, templateRoleType));
            analysisData.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            task.setProgress(90);
            taskMapper.updateById(task);

            AnalysisReport report = new AnalysisReport();
            report.setTaskId(taskId);
            report.setReportName(reportName);
            report.setReportType(normalizedType);
            report.setReportFormat("JSON");
            report.setDescription(summary);
            report.setAnalysisData(objectMapper.writeValueAsString(analysisData));
            report.setIsPublic(0);
            report.setViewCount(0);
            report.setDownloadCount(0);
            report.setGeneratedBy(userId);
            report.setGeneratedAt(LocalDateTime.now());
            report.setCreatedAt(LocalDateTime.now());
            reportMapper.insert(report);

            Map<String, Object> resultSummary = new LinkedHashMap<>();
            resultSummary.put("reportId", report.getId());
            resultSummary.put("reportType", normalizedType);
            resultSummary.put("targetRoleType", templateRoleType);
            resultSummary.put("comparisonCount", asList(analysisData.get("comparisonItems")).size());
            resultSummary.put("recommendationCount", recommendations.size());
            task.setResultSummary(objectMapper.writeValueAsString(resultSummary));

            updateTask(task, "SUCCESS", 100, null);
            createReportReadyNotification(report, userId);
            log.info("Report generation completed: taskId={}, reportId={}", taskId, report.getId());
        } catch (Exception e) {
            log.error("Report generation failed: taskId={}", taskId, e);
            updateTask(task, "FAILED", task.getProgress() == null ? 0 : task.getProgress(), e.getMessage());
        }
    }

    private Map<String, Object> loadOverview() {
        Map<String, Object> overview = new LinkedHashMap<>(safeMap(jobMapper.overviewStats()));
        overview.putIfAbsent("totalJobs", 0L);
        overview.putIfAbsent("avgSalaryMin", 0);
        overview.putIfAbsent("avgSalaryMax", 0);
        return overview;
    }

    private void enrichMarketData(Map<String, Object> data, String reportType, Map<String, Object> userContext) {
        String city = stringValue(userContext.get("targetCityCode"));
        String industry = resolveIndustryHint(userContext);

        data.put("topSkills", marketSkillService.topSkills(12));
        data.put("salaryTrend", safeQueryList(() -> jobMapper.salaryTrend(city, industry)));
        data.put("topCities", safeQueryList(() -> jobMapper.aggregateByCity(10)));
        data.put("topIndustries", safeQueryList(() -> jobMapper.aggregateByIndustry(10)));
        data.put("educationDist", safeQueryList(jobMapper::aggregateByEducation));
        data.put("experienceDist", safeQueryList(jobMapper::aggregateByExperience));

        if ("SUPPLY_DEMAND".equals(reportType)) {
            try {
                data.put("supplyDemand", supplyDemandService.analyzeSkyDemandGap(null));
            } catch (Exception ex) {
                log.warn("Failed to load supply demand section", ex);
                data.put("supplyDemand", Collections.emptyMap());
            }
        }
    }

    private String determineTargetAudience(Map<String, Object> userContext, Integer templateRoleType) {
        if (templateRoleType != null) {
            if (templateRoleType == SysUser.ROLE_ADMIN) {
                return "平台管理员 / 运营负责人";
            }
            if (templateRoleType == SysUser.ROLE_TEACHER) {
                return "教师 / 就业指导老师";
            }
        }
        return StringUtils.hasText(stringValue(userContext.get("profileSummary")))
                ? "学生 / 求职用户"
                : "通用平台用户";
    }

    private String determineReportFocus(
            String reportType,
            Map<String, Object> userContext,
            Map<String, Object> advisory,
            Integer templateRoleType
    ) {
        String role = stringValue(userContext.get("profileSummary"));
        String city = stringValue(userContext.get("targetCityCode"));
        String alignment = stringValue(advisory.get("marketAlignmentScore"));

        if (templateRoleType != null && templateRoleType == SysUser.ROLE_ADMIN) {
            return "围绕平台供需结构、重点技能缺口、用户分层和运营干预优先级展开。";
        }
        if (templateRoleType != null && templateRoleType == SysUser.ROLE_TEACHER) {
            return "围绕学生短板、课程训练对齐度、实训项目补齐和就业辅导动作展开。";
        }
        if ("SALARY".equals(reportType)) {
            return "聚焦薪资区间、薪酬趋势和个人预期与市场基线的差距。";
        }
        if ("SKILL".equals(reportType)) {
            return "聚焦高频技能、能力缺口和补齐优先级。";
        }
        if ("INDUSTRY".equals(reportType)) {
            return "聚焦行业需求分布、岗位结构和目标方向匹配度。";
        }
        if ("SUPPLY_DEMAND".equals(reportType)) {
            return "聚焦供需缺口、紧缺能力和切入机会。";
        }

        StringBuilder focus = new StringBuilder("围绕市场规模、岗位结构、技能热点和用户准备度进行综合诊断");
        if (StringUtils.hasText(role)) {
            focus.append("，目标方向为").append(role);
        }
        if (StringUtils.hasText(city)) {
            focus.append("，目标城市为").append(city);
        }
        if (StringUtils.hasText(alignment)) {
            focus.append("，当前匹配度约为").append(alignment).append("%");
        }
        focus.append("。");
        return focus.toString();
    }

    private List<Map<String, Object>> buildComparisonItems(
            Map<String, Object> userContext,
            Map<String, Object> advisory,
            Map<String, Object> analysisData,
            Integer templateRoleType
    ) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        List<Map<String, Object>> topCities = asList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asList(analysisData.get("topIndustries"));
        List<Map<String, Object>> educationDist = asList(analysisData.get("educationDist"));
        List<Map<String, Object>> experienceDist = asList(analysisData.get("experienceDist"));

        int alignment = parseInt(advisory.get("marketAlignmentScore"));
        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        int matchedSkillCount = parseInt(advisory.get("matchedSkillCount"));
        int marketSkillCount = parseInt(advisory.get("marketSkillCount"));
        List<Map<String, Object>> missingSkills = asList(advisory.get("missingSkills"));
        List<String> skills = readSkills(userContext.get("skills"));
        String city = stringValue(userContext.get("targetCityCode"));

        items.add(comparisonItem(
                "画像完整度",
                completeness + "%",
                "建议不低于 80%",
                completeness >= 80 ? "画像信息较完整，已能支撑较具体的推荐与诊断。" : "当前画像字段缺失较多，会直接降低推荐精度和报告针对性。",
                completeness >= 80 ? "good" : "warn"
        ));

        items.add(comparisonItem(
                "市场匹配度",
                alignment + "%",
                "建议不低于 60%",
                alignment >= 60 ? "现有技能与市场热点已有较高重合，可进入精细化投递与优化阶段。" : "当前技能与高频岗位要求重合偏低，应先补核心能力再提升转化。",
                alignment >= 60 ? "good" : "risk"
        ));

        String expectedMin = stringValue(userContext.get("expectedSalaryMin"));
        String expectedMax = stringValue(userContext.get("expectedSalaryMax"));
        if (StringUtils.hasText(expectedMin) || StringUtils.hasText(expectedMax)) {
            items.add(comparisonItem(
                    "薪资预期",
                    (StringUtils.hasText(expectedMin) ? expectedMin : "--") + " - "
                            + (StringUtils.hasText(expectedMax) ? expectedMax : "--"),
                    stringValue(overview.get("avgSalaryMin")) + " - " + stringValue(overview.get("avgSalaryMax")),
                    "用于判断当前预期是否明显高于市场基线，避免投递层级失真。",
                    "neutral"
            ));
        }

        if (StringUtils.hasText(city)) {
            int rank = findRank(topCities, "city", city);
            items.add(comparisonItem(
                    "目标城市热度",
                    city,
                    rank > 0 ? "城市需求排名 Top " + rank : "未进入头部需求城市",
                    rank > 0 ? "目标城市当前仍有稳定岗位需求，可围绕本地岗位池深挖机会。" : "目标城市不在头部岗位聚集区，建议同步准备跨城或远程机会。",
                    rank > 0 && rank <= 5 ? "good" : "warn"
            ));
        }

        items.add(comparisonItem(
                "技能覆盖",
                skills.size() + " 项",
                missingSkills.isEmpty() ? "核心缺口较少" : "仍有 " + missingSkills.size() + " 项高频缺口",
                missingSkills.isEmpty()
                        ? "当前没有明显核心技能短板，可转向项目表达、简历优化与投递策略。"
                        : "建议优先补齐 " + joinSkillNames(missingSkills, 3) + "，这些能力最影响岗位命中率。",
                missingSkills.isEmpty() ? "good" : "warn"
        ));

        if (marketSkillCount > 0) {
            items.add(comparisonItem(
                    "核心技能对齐",
                    matchedSkillCount + " / " + marketSkillCount,
                    "优先覆盖 Top " + marketSkillCount + " 技能池",
                    matchedSkillCount >= Math.max(3, marketSkillCount / 2)
                            ? "你已覆盖一部分市场高频技能，下一步重点是补强证据与项目表达。"
                            : "当前与核心技能池的重合仍偏低，建议先集中补齐最影响命中率的头部技能。",
                    matchedSkillCount >= Math.max(3, marketSkillCount / 2) ? "good" : "warn"
            ));
        }

        if (!topIndustries.isEmpty()) {
            Map<String, Object> industry = topIndustries.get(0);
            items.add(comparisonItem(
                    "头部行业方向",
                    stringValue(industry.get("industry")),
                    stringValue(industry.get("count")) + " 个岗位样本",
                    "该岗位方向当前样本密度最高，可作为优先对标的岗位与案例来源。",
                    "neutral"
            ));
        }

        if (templateRoleType != null && templateRoleType == SysUser.ROLE_TEACHER) {
            String education = educationDist.isEmpty() ? "暂无学历分布样本" : stringValue(educationDist.get(0).get("education"));
            String experience = experienceDist.isEmpty() ? "暂无经验分布样本" : stringValue(experienceDist.get(0).get("experience"));
            items.add(comparisonItem(
                    "教学对齐点",
                    "课程输出 / 实训项目",
                    education + " + " + experience,
                    "教师模板应把课程成果逐步转成企业可识别的能力证据，尤其对齐主流学历与经验门槛。",
                    "neutral"
            ));
        } else if (templateRoleType != null && templateRoleType == SysUser.ROLE_ADMIN) {
            items.add(comparisonItem(
                    "平台运营视角",
                    "用户当前准备度",
                    "市场头部技能结构",
                    "管理员模板更关注供给与需求的错位，以及哪些能力缺口需要平台优先补供给。",
                    "neutral"
            ));
        }

        return items;
    }

    private List<Map<String, Object>> buildChartCards(Map<String, Object> analysisData) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(buildOverviewCard(analysisData));
        cards.add(chartCard("热门技能需求", "bar", analysisData.get("topSkills"), "skill", "count"));
        cards.add(chartCard("城市需求分布", "bar", analysisData.get("topCities"), "city", "count"));
        cards.add(chartCard("岗位方向分布", "bar", analysisData.get("topIndustries"), "industry", "count"));
        cards.add(chartCard("学历要求分布", "bar", analysisData.get("educationDist"), "education", "count"));
        cards.add(chartCard("经验要求分布", "bar", analysisData.get("experienceDist"), "experience", "count"));
        cards.removeIf(Map::isEmpty);
        return cards;
    }

    private Map<String, Object> buildOverviewCard(Map<String, Object> analysisData) {
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        if (overview.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(simpleChartItem("岗位样本量", overview.get("totalJobs")));
        items.add(simpleChartItem("平均薪资下限", overview.get("avgSalaryMin")));
        items.add(simpleChartItem("平均薪资上限", overview.get("avgSalaryMax")));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", "市场概览");
        card.put("type", "bar");
        card.put("items", items);
        return card;
    }

    private Map<String, Object> simpleChartItem(String label, Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("value", value);
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chartCard(String title, String type, Object source, String labelKey, String valueKey) {
        if (!(source instanceof List<?>)) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object rowObj : (List<?>) source) {
            if (!(rowObj instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) rowObj;
            String label = stringValue(row.get(labelKey));
            Object value = row.get(valueKey);
            if (!StringUtils.hasText(label) || value == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("value", value);
            items.add(item);
            if (items.size() >= 6) {
                break;
            }
        }
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("type", type);
        card.put("items", items);
        return card;
    }

    private List<Map<String, Object>> buildJobSamples(Map<String, Object> userContext) {
        String role = stringValue(userContext.get("profileSummary"));
        String city = stringValue(userContext.get("targetCityCode"));

        List<Map<String, Object>> jobs = StringUtils.hasText(role)
                ? defaultList(jobMapper.searchJobs(role, role, 0, 5))
                : defaultList(jobMapper.hotJobs(5));

        if (!StringUtils.hasText(city)) {
            return jobs;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> job : jobs) {
            String jobCity = stringValue(job.get("city"));
            if (!StringUtils.hasText(jobCity) || jobCity.contains(city) || result.size() < 2) {
                result.add(job);
            }
        }
        return result.isEmpty() ? jobs : result;
    }

    private Map<String, Object> buildRoleTemplate(Integer roleType) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("roleType", roleType);
        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            template.put("roleName", "管理员");
            template.put("templateSummary", "面向平台管理与运营，强调供需结构、热点变化、用户短板分布与运营干预优先级。");
        } else if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            template.put("roleName", "教师");
            template.put("templateSummary", "面向教学与就业指导，强调学生短板、课程改进、项目训练和就业辅导。");
        } else {
            template.put("roleName", "学生 / 普通用户");
            template.put("templateSummary", "面向个人求职与能力提升，强调岗位匹配度、技能缺口、投递策略和行动计划。");
        }
        return template;
    }

    private List<Map<String, Object>> buildActionPlan(
            Map<String, Object> analysisData,
            Map<String, Object> userContext,
            Map<String, Object> advisory,
            Integer templateRoleType
    ) {
        List<Map<String, Object>> plan = new ArrayList<>();
        List<Map<String, Object>> missingSkills = asList(advisory.get("missingSkills"));
        List<String> userSkills = readSkills(userContext.get("skills"));
        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        String role = stringValue(userContext.get("profileSummary"));

        if (templateRoleType != null && templateRoleType == SysUser.ROLE_TEACHER) {
            plan.add(actionItem(1, "锁定重点辅导人群", "根据报告中的匹配度、画像完整度和技能缺口，先识别最需要干预的学生群体。"));
            plan.add(actionItem(2, "按岗位要求调整训练内容", "围绕高频技能和招聘条件，调整课程项目、实训任务和作品集要求。"));
            plan.add(actionItem(3, "把课程产出转成求职证据", "指导学生把课程成果改写为项目成果、业务价值和可量化经历。"));
            return plan;
        }

        if (templateRoleType != null && templateRoleType == SysUser.ROLE_ADMIN) {
            plan.add(actionItem(1, "优先干预低匹配用户", "针对画像不完整、匹配度偏低的用户触发补全引导、技能补齐和路径推荐。"));
            plan.add(actionItem(2, "围绕高频缺口补供给", "将热点技能和重复出现的能力缺口映射到课程、工具、推荐位和运营活动。"));
            plan.add(actionItem(3, "把报告结果接入运营动作", "将缺口技能、热点岗位和低转化群体接入平台通知、推荐和分层服务。"));
            return plan;
        }

        if (completeness < 80) {
            plan.add(actionItem(1, "先补全用户画像", "完善目标岗位、目标城市、技能、个人摘要和薪资预期，提升推荐与报告精度。"));
        }
        if (!missingSkills.isEmpty()) {
            plan.add(actionItem(plan.size() + 1, "优先补齐关键技能", "先处理 " + joinSkillNames(missingSkills, 3) + "，这些能力最影响岗位命中率和面试通过率。"));
        }
        if (!userSkills.isEmpty()) {
            plan.add(actionItem(plan.size() + 1, "把已有技能写成成果证据", "把 " + userSkills.get(0) + " 等能力写进项目成果、指标改善或业务效果，而不是只列名词。"));
        }
        if (StringUtils.hasText(role)) {
            plan.add(actionItem(plan.size() + 1, "按目标岗位定向投递", "围绕“" + role + "”筛选岗位，按报告提示调整简历版本和投递批次。"));
        }
        if (plan.isEmpty()) {
            plan.add(actionItem(1, "持续复盘并更新报告", "每周补全画像、更新技能和求职进展，再重新生成报告观察变化。"));
        }
        return plan;
    }

    private Map<String, Object> actionItem(int priority, String title, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("priority", priority);
        item.put("title", title);
        item.put("detail", detail);
        return item;
    }

    private void createReportReadyNotification(AnalysisReport report, Long userId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle("分析报告已生成");
        notification.setContent("报告《" + report.getReportName() + "》已生成，可在报告中心查看详情并导出 PDF。");
        notification.setNotifyType("REPORT_READY");
        notification.setRefId(report.getId());
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    private void updateTask(AnalysisTask task, String status, Integer progress, String errorMessage) {
        task.setStatus(status);
        task.setProgress(progress);
        if ("RUNNING".equals(status) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        }
        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }
        taskMapper.updateById(task);
    }

    private String sanitizeNarrativeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?is)<think>.*?</think>", "")
                .replace("</think>", "")
                .trim();
    }

    private List<String> sanitizeNarrativeList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<String> cleaned = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String text = normalizeNarrativeItem(item);
            if (StringUtils.hasText(text)) {
                cleaned.add(text);
            }
        }
        return cleaned;
    }

    @SuppressWarnings("unchecked")
    private String normalizeNarrativeItem(Object item) {
        if (item == null) {
            return "";
        }
        if (item instanceof String) {
            return sanitizeNarrativeText((String) item);
        }
        if (item instanceof Map<?, ?>) {
            Map<String, Object> row = (Map<String, Object>) item;
            return sanitizeNarrativeText(firstNonEmpty(row.get("detail"), row.get("title"), row.get("label")));
        }
        return sanitizeNarrativeText(String.valueOf(item));
    }

    private String firstNonEmpty(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTaskParams(String params) {
        if (!StringUtils.hasText(params)) {
            return Collections.emptyMap();
        }
        try {
            Object parsed = objectMapper.readValue(params, Map.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    private Integer resolveTemplateRoleType(Long userId, Map<String, Object> taskParams) {
        Integer requested = parseNullableInt(taskParams.get("targetRoleType"));
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        int currentRole = user == null || user.getRoleType() == null ? SysUser.ROLE_USER : user.getRoleType();
        if (currentRole == SysUser.ROLE_ADMIN && requested != null) {
            return requested;
        }
        return currentRole;
    }

    private String resolveIndustryHint(Map<String, Object> userContext) {
        String role = stringValue(userContext.get("profileSummary"));
        if (!StringUtils.hasText(role)) {
            return "";
        }
        return role.length() > 12 ? role.substring(0, 12) : role;
    }

    private int findRank(List<Map<String, Object>> rows, String key, String target) {
        for (int i = 0; i < rows.size(); i++) {
            String value = stringValue(rows.get(i).get(key));
            if (StringUtils.hasText(value) && value.contains(target)) {
                return i + 1;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
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

    private List<Map<String, Object>> defaultList(List<Map<String, Object>> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private List<String> readSkills(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<String> skills = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                skills.add(String.valueOf(item));
            }
        }
        return skills;
    }

    private String joinSkillNames(List<Map<String, Object>> missingSkills, int limit) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < missingSkills.size() && i < limit; i++) {
            String skill = stringValue(missingSkills.get(i).get("skill"));
            if (StringUtils.hasText(skill)) {
                values.add(skill);
            }
        }
        return values.isEmpty() ? "暂无明显缺口" : String.join("、", values);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int parseInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Integer parseNullableInt(Object value) {
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> comparisonItem(String label, String mine, String market, String insight, String level) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("mine", mine);
        item.put("market", market);
        item.put("insight", insight);
        item.put("level", level);
        return item;
    }

    private List<Map<String, Object>> safeQueryList(QuerySupplier supplier) {
        try {
            List<Map<String, Object>> rows = supplier.get();
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            log.warn("Failed to load report section", ex);
            return Collections.emptyList();
        }
    }

    @FunctionalInterface
    private interface QuerySupplier {
        List<Map<String, Object>> get();
    }
}
