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
import java.util.Arrays;
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

    public static final String REPORT_COMPREHENSIVE = "COMPREHENSIVE";
    public static final String REPORT_JOB_SEEKING = "JOB_SEEKING";
    public static final String REPORT_SKILL_GAP = "SKILL_GAP";
    public static final String REPORT_SUPPLY_DEMAND = "SUPPLY_DEMAND";
    public static final String REPORT_TEACHING_ADVICE = "TEACHING_ADVICE";
    public static final String REPORT_OPERATIONS = "OPERATIONS";
    public static final String REPORT_SALARY = "SALARY";
    public static final String REPORT_INDUSTRY = "INDUSTRY";
    public static final String REPORT_SKILL = "SKILL";

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AnalysisReportMapper reportMapper;
    private final AnalysisTaskMapper taskMapper;
    private final JobPostingMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final SupplyDemandService supplyDemandService;
    private final NotificationMapper notificationMapper;
    private final UserInsightService userInsightService;
    private final MarketSkillService marketSkillService;
    private final SysUserMapper sysUserMapper;

    public ReportGenerationService(AnalysisReportMapper reportMapper, AnalysisTaskMapper taskMapper,
                                   JobPostingMapper jobMapper, ObjectMapper objectMapper,
                                   SupplyDemandService supplyDemandService, NotificationMapper notificationMapper,
                                   UserInsightService userInsightService, MarketSkillService marketSkillService,
                                   SysUserMapper sysUserMapper) {
        this.reportMapper = reportMapper;
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
        this.supplyDemandService = supplyDemandService;
        this.notificationMapper = notificationMapper;
        this.userInsightService = userInsightService;
        this.marketSkillService = marketSkillService;
        this.sysUserMapper = sysUserMapper;
    }

    public Map<String, Object> buildReportCenterMeta(Integer roleType) {
        int normalizedRole = normalizeRoleType(roleType);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("roleType", normalizedRole);
        meta.put("roleLabel", roleLabel(normalizedRole));
        meta.put("moduleTitle", moduleTitle(normalizedRole));
        meta.put("moduleDescription", moduleDescription(normalizedRole));
        meta.put("defaultReportType", defaultReportType(normalizedRole));
        meta.put("defaultReportName", defaultReportName(normalizedRole, defaultReportType(normalizedRole)));
        meta.put("privateListScope", normalizedRole == SysUser.ROLE_ADMIN ? "可查看全站私有报告" : "仅查看本人生成的私有报告");
        meta.put("publicListScope", "公开报告对所有登录或未登录用户可见");
        meta.put("reportTypes", reportTypesForRole(normalizedRole));
        return meta;
    }

    public String defaultReportName(Integer roleType, String reportType) {
        Map<String, Object> profile = reportProfile(normalizeRoleType(roleType), normalizeReportType(reportType));
        return stringValue(profile.get("defaultName"));
    }

    public boolean isReportTypeAllowed(Integer roleType, String reportType) {
        String normalizedType = normalizeReportType(reportType);
        for (Map<String, Object> item : reportTypesForRole(normalizeRoleType(roleType))) {
            if (normalizedType.equals(item.get("code"))) {
                return true;
            }
        }
        return false;
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
            Integer roleType = resolveTemplateRoleType(userId, taskParams);
            Map<String, Object> reportProfile = reportProfile(roleType, normalizedType);
            Map<String, Object> userContext = userInsightService.loadUserContext(userId);
            Map<String, Object> advisory = userInsightService.buildPlatformAdvisory(userId);

            Map<String, Object> analysisData = new LinkedHashMap<>();
            analysisData.put("roleTemplate", buildRoleTemplate(roleType));
            analysisData.put("reportMeta", reportProfile);
            analysisData.put("overview", loadOverview());
            analysisData.put("userContext", userContext);
            analysisData.put("advisory", advisory);
            analysisData.put("targetAudience", reportProfile.get("targetAudience"));
            analysisData.put("reportFocus", reportProfile.get("focus"));
            analysisData.put("templateDescription", reportProfile.get("templateDescription"));
            analysisData.put("listScope", buildReportCenterMeta(roleType).get("privateListScope"));

            fillDataSections(analysisData, roleType, normalizedType, userContext);
            analysisData.put("comparisonItems", buildComparisonItems(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("chartCards", buildChartCards(roleType, normalizedType, analysisData));
            analysisData.put("actionPlan", buildActionPlan(roleType, normalizedType, userContext, advisory, analysisData));

            updateTask(task, "RUNNING", 75, null);

            analysisData.put("diagnosticSummary", buildSummary(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("chartInsights", buildChartInsights(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("recommendations", buildRecommendations(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("generatedAt", LocalDateTime.now().format(DATETIME_FORMATTER));

            updateTask(task, "RUNNING", 92, null);

            AnalysisReport report = new AnalysisReport();
            report.setTaskId(taskId);
            report.setReportName(StringUtils.hasText(reportName) ? reportName : defaultReportName(roleType, normalizedType));
            report.setReportType(normalizedType);
            report.setReportFormat("JSON");
            report.setDescription(stringValue(analysisData.get("diagnosticSummary")));
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
            resultSummary.put("targetRoleType", roleType);
            resultSummary.put("comparisonCount", asMapList(analysisData.get("comparisonItems")).size());
            resultSummary.put("recommendationCount", toStringList(analysisData.get("recommendations")).size());
            task.setResultSummary(objectMapper.writeValueAsString(resultSummary));

            updateTask(task, "SUCCESS", 100, null);
            createReportReadyNotification(report, userId);
        } catch (Exception e) {
            log.error("Report generation failed: taskId={}", taskId, e);
            updateTask(task, "FAILED", task.getProgress() == null ? 0 : task.getProgress(), e.getMessage());
        }
    }

    private void fillDataSections(Map<String, Object> analysisData, Integer roleType, String reportType, Map<String, Object> userContext) {
        boolean student = roleType == SysUser.ROLE_USER;
        boolean teacher = roleType == SysUser.ROLE_TEACHER;
        boolean admin = roleType == SysUser.ROLE_ADMIN;
        boolean includeSalary = student || admin || REPORT_SALARY.equals(reportType) || REPORT_JOB_SEEKING.equals(reportType);
        boolean includeCities = student || admin || REPORT_JOB_SEEKING.equals(reportType) || REPORT_OPERATIONS.equals(reportType);
        boolean includeTracks = teacher || admin || REPORT_INDUSTRY.equals(reportType) || REPORT_SUPPLY_DEMAND.equals(reportType);
        boolean includeRequirements = teacher || admin || REPORT_TEACHING_ADVICE.equals(reportType) || REPORT_SUPPLY_DEMAND.equals(reportType);
        boolean includeJobs = student || REPORT_JOB_SEEKING.equals(reportType);
        boolean includeSupplyDemand = teacher || admin || REPORT_SUPPLY_DEMAND.equals(reportType) || REPORT_OPERATIONS.equals(reportType);

        analysisData.put("topSkills", marketSkillService.topTechnicalSkills(12));
        analysisData.put("topCities", includeCities ? safeQuery(() -> jobMapper.aggregateByCity(8)) : Collections.emptyList());
        analysisData.put("topIndustries", includeTracks ? buildRoleTracks() : Collections.emptyList());
        analysisData.put("educationDist", includeRequirements ? safeQuery(jobMapper::aggregateByEducation) : Collections.emptyList());
        analysisData.put("experienceDist", includeRequirements ? safeQuery(jobMapper::aggregateByExperience) : Collections.emptyList());
        analysisData.put("salaryTrend", includeSalary
                ? safeQuery(() -> jobMapper.salaryTrend(stringValue(userContext.get("targetCityCode")), resolveIndustryHint(userContext)))
                : Collections.emptyList());
        analysisData.put("jobSamples", includeJobs ? buildJobSamples(userContext) : Collections.emptyList());
        analysisData.put("supplyDemand", includeSupplyDemand ? safeSectionMap(() -> supplyDemandService.analyzeSkyDemandGap(null)) : Collections.emptyMap());
    }

    private Map<String, Object> loadOverview() {
        Map<String, Object> overview = new LinkedHashMap<>(safeMap(jobMapper.overviewStats()));
        overview.putIfAbsent("totalJobs", 0L);
        overview.putIfAbsent("avgSalaryMin", 0);
        overview.putIfAbsent("avgSalaryMax", 0);
        return overview;
    }

    private Map<String, Object> buildRoleTemplate(Integer roleType) {
        int normalizedRole = normalizeRoleType(roleType);
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("roleType", normalizedRole);
        template.put("roleName", roleLabel(normalizedRole));
        if (normalizedRole == SysUser.ROLE_ADMIN) {
            template.put("templateSummary", "面向平台运营与管理决策，重点关注供需失衡、用户分层、内容供给和增长抓手。");
        } else if (normalizedRole == SysUser.ROLE_TEACHER) {
            template.put("templateSummary", "面向教学管理与就业指导，重点关注学生能力缺口、课程对齐、实训补强和辅导建议。");
        } else {
            template.put("templateSummary", "面向个人求职与能力提升，重点关注岗位匹配、技能差距、投递策略和成长路径。");
        }
        return template;
    }

    private List<Map<String, Object>> buildComparisonItems(Integer roleType, String reportType,
                                                           Map<String, Object> userContext,
                                                           Map<String, Object> advisory,
                                                           Map<String, Object> analysisData) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> topCities = asMapList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asMapList(analysisData.get("topIndustries"));
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));
        List<String> skills = toStringList(userContext.get("skills"));
        Map<String, Object> overview = safeMap(analysisData.get("overview"));

        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        int alignment = parseInt(advisory.get("marketAlignmentScore"));
        int matchedSkillCount = parseInt(advisory.get("matchedSkillCount"));
        int marketSkillCount = parseInt(advisory.get("marketSkillCount"));

        if (roleType == SysUser.ROLE_TEACHER) {
            items.add(comparisonItem("教学支持重点", "学生技能短板与求职表达", "企业识别的岗位能力证据", "教师端应优先把课程输出转化为可被招聘侧识别的项目成果与能力标签。", "warn"));
            items.add(comparisonItem("供需对齐重点", topValue(topIndustries, "industry", "重点岗位赛道待识别"), "课程与实训设计", "建议围绕高频岗位赛道补齐实训任务、案例库和简历表达模板。", "neutral"));
            items.add(comparisonItem("共性能力缺口", joinSkillNames(missingSkills, 4), "高频岗位技能池", missingSkills.isEmpty() ? "当前未识别出明显共性缺口，可转入就业表达与项目强化。" : "优先把这些高频缺口映射到课程练习、课后作业和辅导任务。", missingSkills.isEmpty() ? "good" : "warn"));
            return items;
        }

        if (roleType == SysUser.ROLE_ADMIN) {
            items.add(comparisonItem("运营关注对象", alignment + "% 市场匹配度", "优先关注低于 60% 的用户群体", "应将低匹配用户沉淀为分层运营对象，驱动内容推荐、通知触达与训练计划。", alignment >= 60 ? "good" : "risk"));
            items.add(comparisonItem("平台内容供给", joinSkillNames(missingSkills, 4), "高频需求技能", missingSkills.isEmpty() ? "技能供给相对平稳，可转向转化效率优化。" : "这些高频需求适合作为专题训练营、课程推荐或运营活动主题。", missingSkills.isEmpty() ? "good" : "warn"));
            items.add(comparisonItem("供需结构焦点", formatNumber(overview.get("totalJobs")) + " 条岗位样本", topValue(topIndustries, "industry", "重点行业待识别"), "平台应优先围绕头部岗位赛道配置内容供给和增长资源。", "neutral"));
            return items;
        }

        items.add(comparisonItem("画像完整度", completeness + "%", "建议达到 80% 以上", completeness >= 80 ? "画像较完整，报告与推荐会更有针对性。" : "建议先补齐目标岗位、目标城市、技能和求职摘要，避免后续建议过泛。", completeness >= 80 ? "good" : "warn"));
        items.add(comparisonItem("岗位匹配度", alignment + "%", "建议达到 60% 以上", alignment >= 60 ? "当前技能与市场热区已有较高重合，可进入定向投递阶段。" : "当前与主流岗位要求仍有明显差距，建议优先补齐关键技能。", alignment >= 60 ? "good" : "risk"));
        items.add(comparisonItem("技能覆盖数", matchedSkillCount + " / " + marketSkillCount, "头部技能池覆盖越高越好", marketSkillCount <= 0 ? "暂未拿到稳定样本，可稍后重试。" : matchedSkillCount >= Math.max(3, marketSkillCount / 2) ? "已经覆盖部分核心能力，下一步要把技能写成项目成果。" : "与头部技能池重合仍偏低，建议先补最影响投递命中率的几项能力。", matchedSkillCount >= Math.max(3, marketSkillCount / 2) ? "good" : "warn"));
        if (StringUtils.hasText(stringValue(userContext.get("targetCityCode")))) {
            String city = stringValue(userContext.get("targetCityCode"));
            int rank = findRank(topCities, "city", city);
            items.add(comparisonItem("目标城市热度", city, rank > 0 ? "岗位需求 Top " + rank : "未进入头部需求城市", rank > 0 ? "目标城市当前仍有稳定机会，适合继续深挖本地岗位池。" : "目标城市不在头部需求区间，可同时关注远程岗位或跨城机会。", rank > 0 ? "good" : "warn"));
        }
        if (REPORT_SALARY.equals(reportType) && (userContext.get("expectedSalaryMin") != null || userContext.get("expectedSalaryMax") != null)) {
            items.add(comparisonItem("薪资预期", firstNonBlank(stringValue(userContext.get("expectedSalaryMin")), "--") + " - " + firstNonBlank(stringValue(userContext.get("expectedSalaryMax")), "--"), formatNumber(overview.get("avgSalaryMin")) + " - " + formatNumber(overview.get("avgSalaryMax")), "建议将预期薪资与市场基线放在同一层级比较，避免投递层级失真。", "neutral"));
        }
        return items;
    }

    private List<Map<String, Object>> buildChartCards(Integer roleType, String reportType, Map<String, Object> analysisData) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(chartCard(roleType == SysUser.ROLE_TEACHER ? "高频岗位能力需求" : "热门技能需求", asMapList(analysisData.get("topSkills")), "skill", "count", "基于当前岗位样本统计生成"));
        cards.add(chartCard(roleType == SysUser.ROLE_ADMIN ? "城市机会分布" : "目标城市机会分布", asMapList(analysisData.get("topCities")), "city", "count", "用于判断岗位机会是否集中"));
        cards.add(chartCard(roleType == SysUser.ROLE_TEACHER ? "重点教学对齐赛道" : "重点岗位赛道", asMapList(analysisData.get("topIndustries")), "industry", "count", "适合作为重点跟进方向"));
        cards.add(chartCard("学历要求分布", asMapList(analysisData.get("educationDist")), "education", "count", "帮助判断教学与投递门槛"));
        cards.add(chartCard("经验要求分布", asMapList(analysisData.get("experienceDist")), "experience", "count", "帮助判断岗位准入层级"));
        cards.removeIf(Map::isEmpty);
        if (REPORT_OPERATIONS.equals(reportType) && cards.isEmpty()) {
            cards.add(chartCard("平台岗位总览", singletonMetricRows(analysisData.get("overview")), "label", "value", "运营总览"));
        }
        return cards;
    }

    private List<Map<String, Object>> buildActionPlan(Integer roleType, String reportType,
                                                      Map<String, Object> userContext,
                                                      Map<String, Object> advisory,
                                                      Map<String, Object> analysisData) {
        List<Map<String, Object>> plan = new ArrayList<>();
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));
        int completeness = parseInt(userContext.get("profileCompletenessScore"));

        if (roleType == SysUser.ROLE_ADMIN) {
            plan.add(actionItem(1, "锁定低匹配度用户群", "对匹配度偏低、画像不完整的用户设置分层提醒、补全引导和专项内容推荐。"));
            plan.add(actionItem(2, "围绕高频缺口补内容供给", "把高频缺口技能映射到课程、训练营、专题活动和推荐资源位。"));
            plan.add(actionItem(3, "把报告指标接入运营动作", "将供需失衡、头部赛道和能力缺口接入通知、推荐和活动运营闭环。"));
            return plan;
        }

        if (roleType == SysUser.ROLE_TEACHER) {
            plan.add(actionItem(1, "筛出重点辅导学生", "优先关注画像不完整、技能短板集中、市场匹配度偏低的学生群体。"));
            plan.add(actionItem(2, "按岗位需求重排课程输出", "将高频技能缺口映射到课程作业、案例训练、实训项目和作品集要求。"));
            plan.add(actionItem(3, "把教学成果转成求职证据", "指导学生把课程产出改写成项目成果、业务价值和可量化经历。"));
            return plan;
        }

        if (completeness < 80) {
            plan.add(actionItem(1, "先补齐个人画像", "完善目标岗位、目标城市、技能、求职摘要和薪资预期，提高推荐与报告精度。"));
        }
        if (!missingSkills.isEmpty()) {
            plan.add(actionItem(plan.size() + 1, "优先补齐关键技能", "建议优先补齐 " + joinSkillNames(missingSkills, 3) + "，这些能力最影响岗位命中率。"));
        }
        if (REPORT_JOB_SEEKING.equals(reportType) || REPORT_COMPREHENSIVE.equals(reportType)) {
            plan.add(actionItem(plan.size() + 1, "围绕目标岗位定制投递", "根据目标岗位关键词拆分简历版本，并优先投递与你现有能力最接近的岗位池。"));
        }
        if (REPORT_SALARY.equals(reportType)) {
            plan.add(actionItem(plan.size() + 1, "校准薪资预期", "将目标薪资区间与城市、行业和岗位层级一起评估，避免高估或低估。"));
        }
        if (plan.isEmpty()) {
            plan.add(actionItem(1, "持续复盘并更新报告", "每周补充新的技能、项目和投递进展，重新生成报告观察变化。"));
        }
        return plan;
    }

    private String buildSummary(Integer roleType, String reportType, Map<String, Object> userContext,
                                Map<String, Object> advisory, Map<String, Object> analysisData) {
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        List<Map<String, Object>> topSkills = asMapList(analysisData.get("topSkills"));
        List<Map<String, Object>> topCities = asMapList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asMapList(analysisData.get("topIndustries"));
        List<Map<String, Object>> jobSamples = asMapList(analysisData.get("jobSamples"));
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));

        String totalJobs = formatNumber(overview.get("totalJobs"));
        String salaryRange = formatNumber(overview.get("avgSalaryMin")) + " - " + formatNumber(overview.get("avgSalaryMax"));
        String topSkill = topValue(topSkills, "skill", "核心技能待识别");
        String topCity = topValue(topCities, "city", "重点城市待识别");
        String topIndustry = topValue(topIndustries, "industry", "重点赛道待识别");
        String sampleJob = topValue(jobSamples, "title", "岗位样本待补充");
        int completeness = parseInt(userContext.get("profileCompletenessScore"));
        int alignment = parseInt(advisory.get("marketAlignmentScore"));

        if (roleType == SysUser.ROLE_ADMIN) {
            return "本次运营分析基于 " + totalJobs + " 条岗位样本生成。当前平台应重点关注 " + topIndustry
                    + " 等头部赛道的人才供需错位，以及 " + joinSkillNames(missingSkills, 3)
                    + " 等高频能力缺口如何转化为课程、推荐和运营动作。对于管理员而言，这份报告更重要的价值不在于单个用户判断，而在于识别哪些群体最需要被唤醒、补齐和转化。";
        }
        if (roleType == SysUser.ROLE_TEACHER) {
            return "本次教学支持报告基于 " + totalJobs + " 条岗位样本生成。当前岗位需求集中在 " + topIndustry
                    + " 等赛道，企业高频关注的能力以 " + topSkill + " 为代表。教师端应优先把学生常见短板与课程输出做映射，把课程作业、实训项目和就业辅导统一到岗位能力证据上，而不是停留在泛化教学建议层面。";
        }
        return "本次个人求职分析基于 " + totalJobs + " 条岗位样本生成。当前主流薪资区间约为 " + salaryRange
                + "，高频能力集中在 " + topSkill + "，机会相对更集中的城市为 " + topCity + "。你的当前画像完整度为 "
                + completeness + "%，市场匹配度约为 " + alignment + "%，建议优先围绕 " + sampleJob + " 这类岗位补齐能力缺口，并把已有技能转化为可展示的项目成果。";
    }

    private List<String> buildChartInsights(Integer roleType, String reportType, Map<String, Object> userContext,
                                            Map<String, Object> advisory, Map<String, Object> analysisData) {
        List<String> insights = new ArrayList<>();
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        List<Map<String, Object>> topSkills = asMapList(analysisData.get("topSkills"));
        List<Map<String, Object>> topCities = asMapList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asMapList(analysisData.get("topIndustries"));
        List<Map<String, Object>> educationDist = asMapList(analysisData.get("educationDist"));
        List<Map<String, Object>> experienceDist = asMapList(analysisData.get("experienceDist"));
        List<Map<String, Object>> jobSamples = asMapList(analysisData.get("jobSamples"));
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));

        insights.add("本报告基于 " + formatNumber(overview.get("totalJobs")) + " 条岗位样本生成，适合作为当前角色的阶段性决策依据。");
        if (!topSkills.isEmpty()) {
            insights.add("高频技能需求首先集中在 " + topValue(topSkills, "skill", "核心技能") + "，说明能力补齐应先从最常见的岗位标签入手。");
        }
        if (!topCities.isEmpty()) {
            insights.add("城市维度上，" + topValue(topCities, "city", "头部城市") + " 仍然保持较高需求密度，适合优先关注机会池。");
        }
        if (!topIndustries.isEmpty()) {
            insights.add("赛道维度上，" + topValue(topIndustries, "industry", "重点赛道") + " 是当前最值得持续跟踪的方向。");
        }
        if (!educationDist.isEmpty() || !experienceDist.isEmpty()) {
            insights.add("岗位门槛更多落在 " + topValue(educationDist, "education", "学历要求待识别") + " 与 " + topValue(experienceDist, "experience", "经验要求待识别") + " 层级，简历表达与训练任务应主动贴近这些标准。");
        }
        if (!jobSamples.isEmpty() && roleType == SysUser.ROLE_USER) {
            insights.add("样本岗位“" + topValue(jobSamples, "title", "目标岗位") + "”可作为近期求职对标对象，用于验证技能、项目和薪资预期是否匹配。");
        }
        if (!missingSkills.isEmpty()) {
            insights.add("当前最需要优先处理的能力缺口集中在 " + joinSkillNames(missingSkills, 3) + "，这些短板直接影响命中率与转化效率。");
        }
        return trimDistinct(insights, 6);
    }

    private List<String> buildRecommendations(Integer roleType, String reportType, Map<String, Object> userContext,
                                              Map<String, Object> advisory, Map<String, Object> analysisData) {
        List<String> recommendations = new ArrayList<>();
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));
        String focusTrack = topValue(asMapList(analysisData.get("topIndustries")), "industry", "");

        if (roleType == SysUser.ROLE_ADMIN) {
            recommendations.add("围绕低匹配度用户建立分层运营策略，把画像补全、技能补齐和岗位推荐串成闭环。");
            recommendations.add("将 " + joinSkillNames(missingSkills, 3) + " 等高频缺口映射为课程专题、训练营或活动主题。");
            recommendations.add("优先为 " + firstNonBlank(focusTrack, "头部岗位赛道") + " 配置更多内容供给和推荐资源位，提升平台转化效率。");
            recommendations.add("定期复盘报告指标，把供需失衡和热点变化接入通知、推荐和运营面板。");
            return trimDistinct(recommendations, 5);
        }

        if (roleType == SysUser.ROLE_TEACHER) {
            recommendations.add("以岗位需求为锚点，调整课程任务、项目题目和实训案例，避免训练内容脱离招聘标准。");
            recommendations.add("将学生常见短板 " + joinSkillNames(missingSkills, 3) + " 拆成可考核、可展示、可写入简历的训练单元。");
            recommendations.add("把课程成果统一成项目说明、业务价值和量化结果，提升学生求职表达质量。");
            recommendations.add("优先针对目标赛道 " + firstNonBlank(focusTrack, "重点岗位方向") + " 设计专项辅导与集中答疑。");
            return trimDistinct(recommendations, 5);
        }

        recommendations.add("先补齐目标岗位、城市、技能和求职摘要，确保报告与推荐足够聚焦。");
        if (!missingSkills.isEmpty()) {
            recommendations.add("优先补齐 " + joinSkillNames(missingSkills, 3) + "，并把学习成果写成项目经历或作品。");
        }
        if (REPORT_SALARY.equals(reportType)) {
            recommendations.add("结合城市和岗位层级重新校准薪资预期，不要只看单个高薪样本。");
        } else {
            recommendations.add("围绕目标岗位拆分简历版本，按技能关键词和项目证据定制投递。");
        }
        recommendations.add("每轮投递后回看反馈数据，持续修正技能补齐顺序与岗位筛选策略。");
        return trimDistinct(recommendations, 5);
    }

    private List<Map<String, Object>> buildJobSamples(Map<String, Object> userContext) {
        String keyword = firstNonBlank(stringValue(userContext.get("profileSummary")), resolveIndustryHint(userContext), "软件工程师");
        List<Map<String, Object>> rows = safeQuery(() -> jobMapper.searchJobs(keyword, keyword, 0, 6));
        return rows.isEmpty() ? safeQuery(() -> jobMapper.hotJobs(6)) : rows;
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
        if (containsAny(text, "ai", "算法", "machine learning", "推荐", "nlp", "深度学习")) return "算法与人工智能";
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
            return normalizeRoleType(fromParams);
        }
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        return user == null || user.getRoleType() == null ? SysUser.ROLE_USER : normalizeRoleType(user.getRoleType());
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
        String type = StringUtils.hasText(reportType) ? reportType.trim().toUpperCase(Locale.ROOT) : REPORT_COMPREHENSIVE;
        List<String> knownTypes = Arrays.asList(
                REPORT_COMPREHENSIVE, REPORT_JOB_SEEKING, REPORT_SKILL_GAP, REPORT_SUPPLY_DEMAND,
                REPORT_TEACHING_ADVICE, REPORT_OPERATIONS, REPORT_SALARY, REPORT_INDUSTRY, REPORT_SKILL
        );
        return knownTypes.contains(type) ? type : REPORT_COMPREHENSIVE;
    }

    private int normalizeRoleType(Integer roleType) {
        if (roleType != null && (roleType == SysUser.ROLE_ADMIN || roleType == SysUser.ROLE_TEACHER)) {
            return roleType;
        }
        return SysUser.ROLE_USER;
    }

    private List<Map<String, Object>> reportTypesForRole(int roleType) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (roleType == SysUser.ROLE_ADMIN) {
            items.add(reportProfile(roleType, REPORT_OPERATIONS));
            items.add(reportProfile(roleType, REPORT_SUPPLY_DEMAND));
            items.add(reportProfile(roleType, REPORT_INDUSTRY));
            items.add(reportProfile(roleType, REPORT_COMPREHENSIVE));
            return items;
        }
        if (roleType == SysUser.ROLE_TEACHER) {
            items.add(reportProfile(roleType, REPORT_SUPPLY_DEMAND));
            items.add(reportProfile(roleType, REPORT_TEACHING_ADVICE));
            items.add(reportProfile(roleType, REPORT_SKILL));
            items.add(reportProfile(roleType, REPORT_COMPREHENSIVE));
            return items;
        }
        items.add(reportProfile(roleType, REPORT_JOB_SEEKING));
        items.add(reportProfile(roleType, REPORT_SKILL_GAP));
        items.add(reportProfile(roleType, REPORT_SALARY));
        items.add(reportProfile(roleType, REPORT_COMPREHENSIVE));
        return items;
    }

    private String defaultReportType(int roleType) {
        if (roleType == SysUser.ROLE_ADMIN) return REPORT_OPERATIONS;
        if (roleType == SysUser.ROLE_TEACHER) return REPORT_SUPPLY_DEMAND;
        return REPORT_JOB_SEEKING;
    }

    private Map<String, Object> reportProfile(int roleType, String reportType) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", reportType);
        item.put("roleType", roleType);
        item.put("roleLabel", roleLabel(roleType));

        switch (reportType) {
            case REPORT_OPERATIONS:
                item.put("label", "平台运营分析");
                item.put("defaultName", "平台运营分析报告");
                item.put("description", "聚焦用户分层、内容供给、转化抓手与运营优先级。");
                item.put("templateDescription", "适合管理员快速判断哪些用户群体、岗位赛道和技能缺口最值得投入运营资源。");
                item.put("focus", "围绕平台增长、供需错位和内容供给效率展开。");
                item.put("targetAudience", "管理员 / 平台运营负责人");
                item.put("entryHint", "优先看用户匹配度、头部赛道和高频缺口。");
                break;
            case REPORT_SUPPLY_DEMAND:
                item.put("label", roleType == SysUser.ROLE_TEACHER ? "供需分析报告" : "平台供需分析");
                item.put("defaultName", roleType == SysUser.ROLE_TEACHER ? "班级供需分析报告" : "平台供需分析报告");
                item.put("description", "聚焦岗位需求、能力缺口与供给侧对齐情况。");
                item.put("templateDescription", roleType == SysUser.ROLE_TEACHER
                        ? "适合教师识别学生群体与岗位需求之间最需要补齐的能力缺口。"
                        : "适合管理员判断平台招聘需求和用户能力之间的结构性矛盾。");
                item.put("focus", "围绕岗位热度、能力缺口和供给侧补强动作展开。");
                item.put("targetAudience", roleType == SysUser.ROLE_TEACHER ? "教师 / 就业指导老师" : "管理员 / 运营负责人");
                item.put("entryHint", "优先看高频岗位赛道、缺口技能和对应补强动作。");
                break;
            case REPORT_TEACHING_ADVICE:
                item.put("label", "教学建议报告");
                item.put("defaultName", "教学建议与课程对齐报告");
                item.put("description", "聚焦课程、实训、项目产出与就业要求如何对齐。");
                item.put("templateDescription", "适合教师把岗位能力要求映射到课程设计、实训任务和辅导动作。");
                item.put("focus", "围绕课程重排、实训补强和简历表达支持展开。");
                item.put("targetAudience", "教师 / 教学管理与就业指导老师");
                item.put("entryHint", "优先看学生共性短板、课程输出缺口和辅导优先级。");
                break;
            case REPORT_JOB_SEEKING:
                item.put("label", "个人求职分析");
                item.put("defaultName", "个人求职分析报告");
                item.put("description", "聚焦岗位匹配、目标城市、技能差距和投递策略。");
                item.put("templateDescription", "适合学生从岗位视角快速看清自己当前该补什么、该投什么、该怎么表达。");
                item.put("focus", "围绕岗位匹配、项目证据和投递动作展开。");
                item.put("targetAudience", "学生 / 求职用户");
                item.put("entryHint", "优先看匹配度、目标城市机会和岗位样本。");
                break;
            case REPORT_SKILL_GAP:
                item.put("label", "技能差距分析");
                item.put("defaultName", "个人技能差距分析报告");
                item.put("description", "聚焦当前技能与高频岗位要求之间的缺口。");
                item.put("templateDescription", "适合学生快速识别最值得优先补齐的核心技能。");
                item.put("focus", "围绕高频技能、缺口排序和补齐路径展开。");
                item.put("targetAudience", "学生 / 求职用户");
                item.put("entryHint", "优先看高频缺口技能与补齐顺序。");
                break;
            case REPORT_SALARY:
                item.put("label", "薪资趋势参考");
                item.put("defaultName", "个人薪资趋势参考报告");
                item.put("description", "聚焦市场薪资区间、预期校准和区域机会。");
                item.put("templateDescription", "适合学生校准薪资预期，避免目标岗位和薪资层级错配。");
                item.put("focus", "围绕薪资趋势、城市机会和岗位层级展开。");
                item.put("targetAudience", "学生 / 求职用户");
                item.put("entryHint", "优先看薪资区间变化与目标城市机会密度。");
                break;
            case REPORT_INDUSTRY:
                item.put("label", "行业走势观察");
                item.put("defaultName", "平台行业走势观察报告");
                item.put("description", "聚焦重点岗位赛道、行业热度和结构变化。");
                item.put("templateDescription", "适合管理员观察热点赛道变化，为内容供给和运营策略提供依据。");
                item.put("focus", "围绕重点行业、岗位热度和机会结构展开。");
                item.put("targetAudience", "管理员 / 运营负责人");
                item.put("entryHint", "优先看头部赛道和变化趋势。");
                break;
            case REPORT_SKILL:
                item.put("label", "能力缺口观察");
                item.put("defaultName", "教学能力缺口观察报告");
                item.put("description", "聚焦岗位高频能力与教学侧能力供给差距。");
                item.put("templateDescription", "适合教师定位哪些技能最适合融入课程、实训和辅导。");
                item.put("focus", "围绕高频技能、教学补位和训练任务展开。");
                item.put("targetAudience", "教师 / 就业指导老师");
                item.put("entryHint", "优先看高频技能与课程补位点。");
                break;
            case REPORT_COMPREHENSIVE:
            default:
                item.put("label", roleType == SysUser.ROLE_ADMIN ? "平台综合报告" : roleType == SysUser.ROLE_TEACHER ? "教学支持总览" : "个人综合报告");
                item.put("defaultName", roleType == SysUser.ROLE_ADMIN ? "平台综合分析报告" : roleType == SysUser.ROLE_TEACHER ? "教学支持综合报告" : "个人综合求职报告");
                item.put("description", "整合关键图表、对比项和行动建议，适合做阶段总览。");
                item.put("templateDescription", "适合当前角色快速获取一份覆盖核心判断与动作建议的综合报告。");
                item.put("focus", "围绕市场结构、能力差距和后续行动建议展开。");
                item.put("targetAudience", roleType == SysUser.ROLE_ADMIN ? "管理员 / 运营负责人" : roleType == SysUser.ROLE_TEACHER ? "教师 / 就业指导老师" : "学生 / 求职用户");
                item.put("entryHint", "适合作为阶段复盘和多维观察入口。");
                break;
        }
        return item;
    }

    private String roleLabel(int roleType) {
        if (roleType == SysUser.ROLE_ADMIN) return "管理员";
        if (roleType == SysUser.ROLE_TEACHER) return "教师";
        return "学生";
    }

    private String moduleTitle(int roleType) {
        if (roleType == SysUser.ROLE_ADMIN) return "运营分析工作台";
        if (roleType == SysUser.ROLE_TEACHER) return "教学支持工作台";
        return "个人求职工作台";
    }

    private String moduleDescription(int roleType) {
        if (roleType == SysUser.ROLE_ADMIN) {
            return "管理员入口优先突出平台运营分析、供需结构和增长抓手，帮助快速判断资源投向。";
        }
        if (roleType == SysUser.ROLE_TEACHER) {
            return "教师入口优先突出供需分析、教学建议和能力缺口观察，帮助把岗位需求映射到课程与辅导。";
        }
        return "学生入口优先突出个人求职分析、技能差距和薪资趋势，帮助围绕目标岗位制定行动。";
    }

    private Map<String, Object> chartCard(String title, List<Map<String, Object>> rows, String labelKey, String valueKey, String subtitle) {
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
        card.put("subtitle", subtitle);
        return card;
    }

    private List<Map<String, Object>> singletonMetricRows(Object overview) {
        Map<String, Object> safeOverview = safeMap(overview);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(metricRow("岗位样本", safeOverview.get("totalJobs")));
        rows.add(metricRow("平均薪资下限", safeOverview.get("avgSalaryMin")));
        rows.add(metricRow("平均薪资上限", safeOverview.get("avgSalaryMax")));
        return rows;
    }

    private Map<String, Object> metricRow(String label, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", toDouble(value));
        return row;
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
        List<String> items = rows.stream()
                .map(row -> stringValue(row.get("skill")))
                .filter(StringUtils::hasText)
                .limit(limit)
                .collect(Collectors.toList());
        return items.isEmpty() ? "关键能力待识别" : String.join("、", items);
    }

    private List<String> trimDistinct(List<String> values, int limit) {
        Set<String> dedup = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                dedup.add(value.trim());
            }
            if (dedup.size() >= limit) {
                break;
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

    @SuppressWarnings("unchecked")
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

    private Map<String, Object> safeSectionMap(MapSupplier supplier) {
        try {
            Map<String, Object> value = supplier.get();
            return value == null ? Collections.emptyMap() : value;
        } catch (Exception ex) {
            log.warn("Failed to query report map section", ex);
            return Collections.emptyMap();
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

    private String topValue(List<Map<String, Object>> rows, String key, String fallback) {
        if (rows == null || rows.isEmpty()) {
            return fallback;
        }
        String value = stringValue(rows.get(0).get(key));
        return StringUtils.hasText(value) ? value : fallback;
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

    @FunctionalInterface
    private interface QuerySupplier {
        List<Map<String, Object>> get();
    }

    @FunctionalInterface
    private interface MapSupplier {
        Map<String, Object> get();
    }
}
