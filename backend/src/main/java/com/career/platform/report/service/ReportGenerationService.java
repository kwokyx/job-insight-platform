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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    public ReportGenerationService(AnalysisReportMapper reportMapper, AnalysisTaskMapper taskMapper,
                                   JobPostingMapper jobMapper, ObjectMapper objectMapper,
                                   LlmReportWriterService llmReportWriterService, SupplyDemandService supplyDemandService,
                                   NotificationMapper notificationMapper, UserInsightService userInsightService,
                                   MarketSkillService marketSkillService, SysUserMapper sysUserMapper) {
        this.reportMapper = reportMapper;
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
        this.llmReportWriterService = llmReportWriterService;
        this.supplyDemandService = supplyDemandService;
        this.notificationMapper = notificationMapper;
        this.userInsightService = userInsightService;
        this.marketSkillService = marketSkillService;
        this.sysUserMapper = sysUserMapper;
    }

    @Async("reportExecutor")
    public void executeReportGeneration(Long taskId, String reportType, String reportName, Long userId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Report task not found: {}", taskId);
            return;
        }

        try {
            updateTask(task, "RUNNING", 10, null);

            String normalizedType = normalizeReportType(reportType);
            Map<String, Object> taskParams = parseTaskParams(task.getParams());
            Integer templateRoleType = resolveTemplateRoleType(userId, taskParams);
            Map<String, Object> userContext = userInsightService.loadUserContext(userId);
            Map<String, Object> advisory = userInsightService.buildPlatformAdvisory(userId);

            Map<String, Object> analysisData = new LinkedHashMap<>();
            analysisData.put("overview", loadOverview());
            analysisData.put("roleTemplate", buildRoleTemplate(templateRoleType));
            analysisData.put("userContext", userContext);
            analysisData.put("advisory", advisory);
            analysisData.put("topSkills", marketSkillService.topTechnicalSkills(12));
            analysisData.put("topCities", safeQuery(() -> jobMapper.aggregateByCity(10)));
            analysisData.put("topIndustries", buildRoleTracks());
            analysisData.put("educationDist", safeQuery(jobMapper::aggregateByEducation));
            analysisData.put("experienceDist", safeQuery(jobMapper::aggregateByExperience));
            analysisData.put("salaryTrend", safeQuery(() -> jobMapper.salaryTrend(
                    stringValue(userContext.get("targetCityCode")),
                    resolveIndustryHint(userContext)
            )));
            analysisData.put("jobSamples", buildJobSamples(userContext));
            analysisData.put("targetAudience", determineTargetAudience(userContext, templateRoleType));
            analysisData.put("reportFocus", determineReportFocus(normalizedType, userContext, advisory, templateRoleType));
            analysisData.put("comparisonItems", buildComparisonItems(userContext, advisory, analysisData, templateRoleType));
            analysisData.put("chartCards", buildChartCards(analysisData));

            if ("SUPPLY_DEMAND".equals(normalizedType)) {
                try {
                    analysisData.put("supplyDemand", supplyDemandService.analyzeSkyDemandGap(null));
                } catch (Exception ex) {
                    log.warn("Failed to load supply-demand section", ex);
                    analysisData.put("supplyDemand", Collections.emptyMap());
                }
            }

            updateTask(task, "RUNNING", 70, null);

            Map<String, Object> narrative = llmReportWriterService.generateNarrative(analysisData, normalizedType, userContext);
            List<String> chartInsights = sanitizeStringList(narrative.get("chartInsights"), 6);
            List<String> recommendations = sanitizeStringList(narrative.get("recommendations"), 5);
            String summary = sanitizeText(stringValue(narrative.get("summary")));

            analysisData.put("diagnosticSummary", summary);
            analysisData.put("chartInsights", chartInsights);
            analysisData.put("recommendations", recommendations);
            analysisData.put("actionPlan", buildActionPlan(userContext, advisory, templateRoleType));
            analysisData.put("generatedAt", LocalDateTime.now().format(DATETIME_FORMATTER));

            updateTask(task, "RUNNING", 90, null);

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
            resultSummary.put("comparisonCount", asMapList(analysisData.get("comparisonItems")).size());
            resultSummary.put("recommendationCount", recommendations.size());
            task.setResultSummary(objectMapper.writeValueAsString(resultSummary));

            updateTask(task, "SUCCESS", 100, null);
            createReportReadyNotification(report, userId);
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

    private Map<String, Object> buildRoleTemplate(Integer roleType) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("roleType", roleType);
        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            template.put("roleName", "管理员");
            template.put("templateSummary", "面向平台管理与运营决策，强调供需结构、用户分层、热点变化和平台干预优先级。");
            return template;
        }
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            template.put("roleName", "教师");
            template.put("templateSummary", "面向教学改革和就业指导，强调学生短板、课程对齐和训练项目补强。");
            return template;
        }
        template.put("roleName", "学生/普通用户");
        template.put("templateSummary", "面向个人求职与能力提升，强调岗位匹配、技能差距和投递策略。");
        return template;
    }

    private String determineTargetAudience(Map<String, Object> userContext, Integer roleType) {
        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            return "平台管理员 / 运营负责人";
        }
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            return "教师 / 就业指导老师";
        }
        return StringUtils.hasText(stringValue(userContext.get("profileSummary"))) ? "学生 / 求职用户" : "平台普通用户";
    }

    private String determineReportFocus(String reportType, Map<String, Object> userContext, Map<String, Object> advisory, Integer roleType) {
        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            return "围绕平台供需结构、重点能力缺口、用户分层和干预优先级展开。";
        }
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            return "围绕学生短板、课程训练对齐、项目补强和就业指导动作展开。";
        }
        switch (reportType) {
            case "SALARY":
                return "聚焦薪资区间、薪资趋势以及个人预期与市场基线之间的差距。";
            case "SKILL":
                return "聚焦高频技能、能力缺口和补齐优先级。";
            case "INDUSTRY":
                return "聚焦行业需求分布、岗位赛道和目标方向匹配度。";
            case "SUPPLY_DEMAND":
                return "聚焦供需缺口、紧缺能力和切入机会。";
            default:
                return "围绕市场规模、岗位结构、技能热点和个人准备度进行综合诊断。";
        }
    }

    private List<Map<String, Object>> buildJobSamples(Map<String, Object> userContext) {
        String keyword = firstNonBlank(stringValue(userContext.get("profileSummary")), resolveIndustryHint(userContext), "工程师");
        List<Map<String, Object>> rows = safeQuery(() -> jobMapper.searchJobs(keyword, keyword, 0, 6));
        return rows.isEmpty() ? safeQuery(() -> jobMapper.hotJobs(6)) : rows;
    }

    private List<Map<String, Object>> buildComparisonItems(
            Map<String, Object> userContext,
            Map<String, Object> advisory,
            Map<String, Object> analysisData,
            Integer templateRoleType
    ) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        List<Map<String, Object>> topCities = asMapList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asMapList(analysisData.get("topIndustries"));
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));
        List<String> skills = toStringList(userContext.get("skills"));

        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        int alignment = parseInt(advisory.get("marketAlignmentScore"));
        int matchedSkillCount = parseInt(advisory.get("matchedSkillCount"));
        int marketSkillCount = parseInt(advisory.get("marketSkillCount"));
        String city = stringValue(userContext.get("targetCityCode"));

        items.add(comparisonItem(
                "画像完整度",
                completeness + "%",
                "建议不低于 80%",
                completeness >= 80 ? "当前画像较完整，推荐和报告会更具针对性。" : "当前画像缺少关键字段，建议先补全目标岗位、城市、技能和求职摘要。",
                completeness >= 80 ? "good" : "warn"
        ));
        items.add(comparisonItem(
                "市场匹配度",
                alignment + "%",
                "建议不低于 60%",
                alignment >= 60 ? "现有能力与市场热点已有较高重合，可进入精细化投递阶段。" : "当前和主流岗位要求仍有明显差距，应先补齐核心能力。",
                alignment >= 60 ? "good" : "risk"
        ));

        if (StringUtils.hasText(stringValue(userContext.get("expectedSalaryMin"))) || StringUtils.hasText(stringValue(userContext.get("expectedSalaryMax")))) {
            items.add(comparisonItem(
                    "薪资预期",
                    firstNonBlank(stringValue(userContext.get("expectedSalaryMin")), "--") + " - " + firstNonBlank(stringValue(userContext.get("expectedSalaryMax")), "--"),
                    formatNumber(overview.get("avgSalaryMin")) + " - " + formatNumber(overview.get("avgSalaryMax")),
                    "用于判断当前预期是否显著高于市场基线，避免投递层级失真。",
                    "neutral"
            ));
        }

        if (StringUtils.hasText(city)) {
            int rank = findRank(topCities, "city", city);
            items.add(comparisonItem(
                    "目标城市热度",
                    city,
                    rank > 0 ? "城市需求 Top " + rank : "未进入头部需求城市",
                    rank > 0 ? "目标城市当前仍有稳定岗位需求，可围绕本地岗位池深挖机会。" : "目标城市不在头部岗位聚集区，建议同步关注跨城或远程岗位。",
                    rank > 0 && rank <= 5 ? "good" : "warn"
            ));
        }

        items.add(comparisonItem(
                "技能覆盖",
                skills.size() + " 项",
                missingSkills.isEmpty() ? "核心缺口较少" : "仍有 " + missingSkills.size() + " 项高频缺口",
                missingSkills.isEmpty() ? "当前没有明显核心短板，可以转向简历优化和投递策略。" : "优先补齐 " + joinSkillNames(missingSkills, 3) + "，这些能力最影响岗位命中率。",
                missingSkills.isEmpty() ? "good" : "warn"
        ));

        if (marketSkillCount > 0) {
            items.add(comparisonItem(
                    "核心技能对齐",
                    matchedSkillCount + " / " + marketSkillCount,
                    "优先覆盖 Top " + marketSkillCount + " 技能池",
                    matchedSkillCount >= Math.max(3, marketSkillCount / 2)
                            ? "已覆盖部分高频技能，下一步重点是把技能转成可展示成果。"
                            : "和头部技能池重合仍偏低，建议先补最影响转化的几项能力。",
                    matchedSkillCount >= Math.max(3, marketSkillCount / 2) ? "good" : "warn"
            ));
        }

        if (!topIndustries.isEmpty()) {
            Map<String, Object> topTrack = topIndustries.get(0);
            items.add(comparisonItem(
                    "重点岗位赛道",
                    stringValue(topTrack.get("industry")),
                    formatNumber(topTrack.get("count")) + " 个岗位样本",
                    "该赛道在当前样本中密度最高，适合作为优先对标方向。",
                    "neutral"
            ));
        }

        if (templateRoleType != null && templateRoleType == SysUser.ROLE_TEACHER) {
            items.add(comparisonItem(
                    "教学对齐点",
                    "课程输出 / 实训项目",
                    "企业识别的能力证据",
                    "教师角色应重点把课程成果转换成可被招聘方识别的项目证据和能力标签。",
                    "neutral"
            ));
        }
        if (templateRoleType != null && templateRoleType == SysUser.ROLE_ADMIN) {
            items.add(comparisonItem(
                    "平台运营视角",
                    "用户当前准备度",
                    "市场头部技能结构",
                    "管理员角色应重点关注能力缺口集中在哪些人群，并将其转化为平台干预动作。",
                    "neutral"
            ));
        }
        return items;
    }

    private List<Map<String, Object>> buildChartCards(Map<String, Object> analysisData) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(chartCard("热门技能需求", asMapList(analysisData.get("topSkills")), "skill", "count"));
        cards.add(chartCard("城市需求分布", asMapList(analysisData.get("topCities")), "city", "count"));
        cards.add(chartCard("岗位赛道分布", asMapList(analysisData.get("topIndustries")), "industry", "count"));
        cards.add(chartCard("学历要求分布", asMapList(analysisData.get("educationDist")), "education", "count"));
        cards.add(chartCard("经验要求分布", asMapList(analysisData.get("experienceDist")), "experience", "count"));
        cards.removeIf(Map::isEmpty);
        return cards;
    }

    private List<Map<String, Object>> buildActionPlan(Map<String, Object> userContext, Map<String, Object> advisory, Integer templateRoleType) {
        List<Map<String, Object>> plan = new ArrayList<>();
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));
        List<String> userSkills = toStringList(userContext.get("skills"));
        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        String role = stringValue(userContext.get("profileSummary"));

        if (templateRoleType != null && templateRoleType == SysUser.ROLE_ADMIN) {
            plan.add(actionItem(1, "优先干预低匹配用户", "针对画像不完整、匹配度偏低的用户触发补全引导、技能补齐和推荐分层。"));
            plan.add(actionItem(2, "围绕高频缺口补内容供给", "把热点技能和重复出现的能力短板映射到课程、工具和运营活动。"));
            plan.add(actionItem(3, "把报告结果接入运营动作", "将缺口技能、热点岗位和低转化群体接入通知、推荐和分层服务。"));
            return plan;
        }
        if (templateRoleType != null && templateRoleType == SysUser.ROLE_TEACHER) {
            plan.add(actionItem(1, "锁定重点辅导人群", "根据匹配度、画像完整度和技能缺口识别最需要干预的学生群体。"));
            plan.add(actionItem(2, "按岗位要求调整训练内容", "围绕高频技能和招聘条件优化课程项目、实训任务和作品集要求。"));
            plan.add(actionItem(3, "把课程成果转为求职证据", "指导学生把课程成果写成项目结果、业务价值和量化经历。"));
            return plan;
        }

        if (completeness < 80) {
            plan.add(actionItem(1, "先补全用户画像", "完善目标岗位、城市、技能、个人摘要和薪资预期，提升推荐与报告精度。"));
        }
        if (!missingSkills.isEmpty()) {
            plan.add(actionItem(plan.size() + 1, "优先补齐关键技能", "建议优先补齐 " + joinSkillNames(missingSkills, 3) + "，这些能力最影响岗位命中率。"));
        }
        if (!userSkills.isEmpty()) {
            plan.add(actionItem(plan.size() + 1, "把已有技能写成成果", "不要只列技能名词，应把技能落到项目结果、数据指标和业务效果上。"));
        }
        if (StringUtils.hasText(role)) {
            plan.add(actionItem(plan.size() + 1, "按目标岗位定向投递", "围绕“" + role + "”筛选岗位，并按岗位关键词定制简历版本。"));
        }
        if (plan.isEmpty()) {
            plan.add(actionItem(1, "持续复盘并更新报告", "每周补充新技能和求职进展，重新生成报告观察变化。"));
        }
        return plan;
    }

    private List<Map<String, Object>> buildRoleTracks() {
        List<Map<String, Object>> jobs = safeQuery(() -> jobMapper.hotJobs(500));
        if (jobs.isEmpty()) {
            return safeQuery(() -> jobMapper.aggregateByIndustry(8));
        }

        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> job : jobs) {
            String track = classifyRoleTrack(stringValue(job.get("title")), stringValue(job.get("industryName")));
            counter.merge(track, 1, Integer::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        counter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("industry", entry.getKey());
                    row.put("count", entry.getValue());
                    result.add(row);
                });
        return result;
    }

    private String classifyRoleTrack(String title, String industryName) {
        String text = (firstNonBlank(title, "") + " " + firstNonBlank(industryName, "")).toLowerCase(Locale.ROOT);
        if (containsAny(text, "java", "spring", "backend", "后端", "服务端", "golang", "php", ".net")) return "后端开发";
        if (containsAny(text, "frontend", "前端", "vue", "react", "javascript", "web")) return "前端开发";
        if (containsAny(text, "全栈", "full stack", "fullstack")) return "全栈开发";
        if (containsAny(text, "data analyst", "数据分析", "bi", "分析师")) return "数据分析";
        if (containsAny(text, "data engineer", "数据工程", "etl", "数仓", "spark", "flink", "hadoop")) return "数据工程";
        if (containsAny(text, "ai", "算法", "machine learning", "推荐", "nlp", "深度学习")) return "算法与AI";
        if (containsAny(text, "qa", "测试", "自动化测试")) return "测试与质量";
        if (containsAny(text, "devops", "运维", "sre", "docker", "k8s", "linux")) return "运维与云平台";
        if (containsAny(text, "product", "产品")) return "产品与策略";
        if (containsAny(text, "ui", "ux", "设计", "交互")) return "设计体验";
        return "综合岗位";
    }

    private void createReportReadyNotification(AnalysisReport report, Long userId) {
        if (userId == null || report == null || report.getId() == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle("报告生成完成");
            notification.setContent("《" + report.getReportName() + "》已生成，可在报告中心查看和导出。");
            notification.setNotifyType("REPORT_READY");
            notification.setRefId(report.getId());
            notification.setIsRead(0);
            notificationMapper.insert(notification);
        } catch (Exception ex) {
            log.warn("Failed to create report notification", ex);
        }
    }

    private Integer resolveTemplateRoleType(Long userId, Map<String, Object> taskParams) {
        Integer fromParams = parseNullableInt(taskParams.get("targetRoleType"));
        if (fromParams != null) {
            return fromParams;
        }
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        return user == null || user.getRoleType() == null ? SysUser.ROLE_USER : user.getRoleType();
    }

    private Map<String, Object> parseTaskParams(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String resolveIndustryHint(Map<String, Object> userContext) {
        String role = stringValue(userContext.get("profileSummary")).toLowerCase(Locale.ROOT);
        if (containsAny(role, "ai", "算法", "机器学习")) return "人工智能";
        if (containsAny(role, "data", "数据")) return "数据";
        if (containsAny(role, "java", "backend", "后端")) return "软件";
        if (containsAny(role, "frontend", "前端", "vue", "react")) return "互联网";
        return "";
    }

    private String normalizeReportType(String reportType) {
        return StringUtils.hasText(reportType) ? reportType.trim().toUpperCase(Locale.ROOT) : "COMPREHENSIVE";
    }

    private void updateTask(AnalysisTask task, String status, Integer progress, String errorMessage) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setErrorMessage(errorMessage);
        if ("RUNNING".equals(status) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    private Map<String, Object> chartCard(String title, List<Map<String, Object>> rows, String labelKey, String valueKey) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        double max = rows.stream().mapToDouble(row -> toDouble(row.get(valueKey))).max().orElse(1D);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows.stream().limit(8).collect(Collectors.toList())) {
            Map<String, Object> item = new LinkedHashMap<>();
            double value = toDouble(row.get(valueKey));
            item.put("label", stringValue(row.get(labelKey)));
            item.put("value", value);
            item.put("valueText", formatNumber(value));
            item.put("percent", max <= 0 ? 0 : Math.min(100, Math.round(value * 100 / max)));
            items.add(item);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("type", "bar");
        card.put("items", items);
        card.put("subtitle", "基于当前样本统计生成");
        return card;
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

    private Map<String, Object> actionItem(int priority, String title, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("priority", priority);
        item.put("title", title);
        item.put("detail", detail);
        return item;
    }

    private int findRank(List<Map<String, Object>> rows, String key, String expected) {
        for (int i = 0; i < rows.size(); i++) {
            if (expected.equalsIgnoreCase(stringValue(rows.get(i).get(key)))) {
                return i + 1;
            }
        }
        return -1;
    }

    private String joinSkillNames(List<Map<String, Object>> rows, int limit) {
        return rows.stream()
                .map(row -> stringValue(row.get("skill")))
                .filter(StringUtils::hasText)
                .limit(limit)
                .collect(Collectors.joining("、"));
    }

    private List<String> sanitizeStringList(Object value, int limit) {
        Set<String> dedup = new LinkedHashSet<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = sanitizeText(stringValue(item));
                if (StringUtils.hasText(text)) {
                    dedup.add(text);
                }
                if (dedup.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(dedup);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item).trim());
            }
        }
        return result;
    }

    private String sanitizeText(String text) {
        return firstNonBlank(text, "").replace("�", "").trim();
    }

    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private List<Map<String, Object>> safeQuery(QuerySupplier supplier) {
        try {
            List<Map<String, Object>> rows = supplier.get();
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            log.warn("Failed to query report section", ex);
            return Collections.emptyList();
        }
    }

    private String formatNumber(Object value) {
        if (value == null) {
            return "--";
        }
        if (value instanceof Integer || value instanceof Long) {
            return String.valueOf(value);
        }
        double number = toDouble(value);
        if (Math.abs(number - Math.round(number)) < 0.01d) {
            return String.valueOf((long) Math.round(number));
        }
        return String.format(Locale.CHINA, "%.2f", number);
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean containsAny(String text, String... values) {
        String normalized = firstNonBlank(text, "").toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (normalized.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface QuerySupplier {
        List<Map<String, Object>> get();
    }
}
