package com.career.platform.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.TeachingReformService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.subscription.entity.Notification;
import com.career.platform.subscription.mapper.NotificationMapper;
import com.career.platform.subscription.service.SubscriptionMailService;
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
    private final TeachingReformService teachingReformService;
    private final SubscriptionMailService subscriptionMailService;

    public ReportGenerationService(AnalysisReportMapper reportMapper, AnalysisTaskMapper taskMapper,
                                   JobPostingMapper jobMapper, ObjectMapper objectMapper,
                                   SupplyDemandService supplyDemandService, NotificationMapper notificationMapper,
                                   UserInsightService userInsightService, MarketSkillService marketSkillService,
                                   SysUserMapper sysUserMapper, TeachingReformService teachingReformService,
                                   SubscriptionMailService subscriptionMailService) {
        this.reportMapper = reportMapper;
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
        this.supplyDemandService = supplyDemandService;
        this.notificationMapper = notificationMapper;
        this.userInsightService = userInsightService;
        this.marketSkillService = marketSkillService;
        this.sysUserMapper = sysUserMapper;
        this.teachingReformService = teachingReformService;
        this.subscriptionMailService = subscriptionMailService;
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
            Map<String, Object> versioning = buildReportVersioning(userId, normalizedType);

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
            analysisData.put("reportGovernance", buildReportGovernance(roleType, normalizedType, taskParams, versioning));
            analysisData.put("reportVersioning", versioning);

            fillDataSections(analysisData, roleType, normalizedType, userContext);
            analysisData.put("deepInsights", buildDeepInsightSnapshot(taskParams, userContext, roleType, normalizedType, analysisData));
            analysisData.put("industryPerspective", buildIndustryPerspective(normalizedType, analysisData, taskParams));
            if (roleType == SysUser.ROLE_TEACHER || REPORT_TEACHING_ADVICE.equals(normalizedType) || REPORT_SUPPLY_DEMAND.equals(normalizedType)) {
                analysisData.put("teachingReform", teachingReformService.buildTeachingReformAnalysis(userId, stringValue(taskParams.get("major"))));
            }
            analysisData.put("roleDigest", buildRoleDigest(roleType, userContext, advisory, analysisData));
            analysisData.put("comparisonItems", buildComparisonItems(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("chartCards", buildChartCards(roleType, normalizedType, analysisData));
            analysisData.put("actionPlan", buildActionPlan(roleType, normalizedType, userContext, advisory, analysisData));

            updateTask(task, "RUNNING", 75, null);

            analysisData.put("diagnosticSummary", buildSummary(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("chartInsights", buildChartInsights(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("recommendations", buildRecommendations(roleType, normalizedType, userContext, advisory, analysisData));
            analysisData.put("generatedAt", LocalDateTime.now().format(DATETIME_FORMATTER));
            analysisData.put("sampleConfidence", buildSampleConfidence(analysisData));

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
        boolean includeSalary = student || admin;
        boolean includeCities = student || admin;
        boolean includeTracks = teacher || admin;
        boolean includeRequirements = teacher;
        boolean includeJobs = student;
        boolean includeSupplyDemand = teacher || admin;

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

    private Map<String, Object> buildReportGovernance(Integer roleType, String reportType,
                                                      Map<String, Object> taskParams,
                                                      Map<String, Object> versioning) {
        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("version", "2026.04");
        governance.put("roleType", roleType);
        governance.put("reportType", reportType);
        governance.put("scope", REPORT_INDUSTRY.equals(reportType) ? "industry" : "role-self");
        governance.put("timeWindowMonths", 12);
        governance.put("cityFilter", stringValue(taskParams.get("city")));
        governance.put("industryFilter", stringValue(taskParams.get("industry")));
        governance.put("majorFilter", stringValue(taskParams.get("major")));
        governance.put("versionNo", parseInt(versioning.get("versionNo")));
        governance.put("versionLabel", stringValue(versioning.get("versionLabel")));
        governance.put("previousReportId", versioning.get("previousReportId"));
        governance.put("lineageMode", "generatedBy-plus-reportType");
        return governance;
    }

    public List<Map<String, Object>> listReportVersions(Long reportId, Long currentUserId, Integer currentRoleType) {
        AnalysisReport target = reportMapper.selectById(reportId);
        if (target == null) {
            return Collections.emptyList();
        }
        boolean admin = normalizeRoleType(currentRoleType) == SysUser.ROLE_ADMIN;
        if (!admin && currentUserId != null && !currentUserId.equals(target.getGeneratedBy())) {
            return Collections.emptyList();
        }

        List<AnalysisReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<AnalysisReport>()
                        .eq(AnalysisReport::getGeneratedBy, target.getGeneratedBy())
                        .eq(AnalysisReport::getReportType, target.getReportType())
                        .orderByDesc(AnalysisReport::getGeneratedAt)
                        .orderByDesc(AnalysisReport::getId)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < reports.size(); i++) {
            AnalysisReport report = reports.get(i);
            Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
            Map<String, Object> versioning = safeMap(analysisData.get("reportVersioning"));
            Map<String, Object> governance = safeMap(analysisData.get("reportGovernance"));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reportId", report.getId());
            item.put("reportName", report.getReportName());
            item.put("reportType", report.getReportType());
            item.put("generatedAt", report.getGeneratedAt());
            item.put("isCurrent", report.getId().equals(reportId));
            item.put("versionNo", resolveVersionNo(reports.size(), i, versioning, governance));
            item.put("versionLabel", firstNonBlank(
                    stringValue(versioning.get("versionLabel")),
                    stringValue(governance.get("versionLabel")),
                    "V" + resolveVersionNo(reports.size(), i, versioning, governance)
            ));
            item.put("changeSummary", stringValue(versioning.get("changeSummary")));
            item.put("scope", firstNonBlank(stringValue(governance.get("scope")), "role-self"));
            item.put("previousReportId", versioning.get("previousReportId"));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildReportVersioning(Long userId, String reportType) {
        Map<String, Object> versioning = new LinkedHashMap<>();
        AnalysisReport previous = latestOwnedReport(userId, reportType);
        int versionNo = previous == null ? 1 : resolveVersionNo(parseAnalysisData(previous.getAnalysisData()), previous.getId()) + 1;
        versioning.put("versionNo", versionNo);
        versioning.put("versionLabel", "V" + versionNo);
        versioning.put("lineageKey", userId + ":" + reportType);
        versioning.put("previousReportId", previous == null ? null : previous.getId());
        versioning.put("previousGeneratedAt", previous == null ? null : previous.getGeneratedAt());
        versioning.put("changeSummary", previous == null ? "首个版本，建立报告基线。" : "相较上一版本刷新样本、趋势指标与行动建议。");
        return versioning;
    }

    private AnalysisReport latestOwnedReport(Long userId, String reportType) {
        if (userId == null || !StringUtils.hasText(reportType)) {
            return null;
        }
        IPage<AnalysisReport> page = reportMapper.selectPage(
                new Page<>(1, 1),
                new LambdaQueryWrapper<AnalysisReport>()
                        .eq(AnalysisReport::getGeneratedBy, userId)
                        .eq(AnalysisReport::getReportType, reportType)
                        .orderByDesc(AnalysisReport::getGeneratedAt)
                        .orderByDesc(AnalysisReport::getId)
        );
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    private Map<String, Object> parseAnalysisData(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse report analysis data", ex);
            return new LinkedHashMap<>();
        }
    }

    private int resolveVersionNo(Map<String, Object> analysisData, Long reportId) {
        Map<String, Object> versioning = safeMap(analysisData.get("reportVersioning"));
        Map<String, Object> governance = safeMap(analysisData.get("reportGovernance"));
        return resolveVersionNo(1, 0, versioning, governance);
    }

    private int resolveVersionNo(int total, int index, Map<String, Object> versioning, Map<String, Object> governance) {
        int versionNo = parseInt(versioning.get("versionNo"));
        if (versionNo > 0) {
            return versionNo;
        }
        versionNo = parseInt(governance.get("versionNo"));
        if (versionNo > 0) {
            return versionNo;
        }
        return Math.max(1, total - index);
    }

    private Map<String, Object> buildIndustryPerspective(String reportType, Map<String, Object> analysisData, Map<String, Object> taskParams) {
        Map<String, Object> deepInsights = safeMap(analysisData.get("deepInsights"));
        Map<String, Object> marketPulse = safeMap(deepInsights.get("marketPulse"));
        Map<String, Object> cityConcentration = safeMap(deepInsights.get("cityConcentration"));
        Map<String, Object> sample = safeMap(deepInsights.get("sample"));
        Map<String, Object> perspective = new LinkedHashMap<>();
        perspective.put("reportScope", REPORT_INDUSTRY.equals(reportType) ? "industry-analysis" : "role-analysis");
        perspective.put("focusIndustry", firstNonBlank(stringValue(taskParams.get("industry")), topValue(asMapList(analysisData.get("topIndustries")), "industry", "")));
        perspective.put("focusCity", firstNonBlank(stringValue(taskParams.get("city")), topValue(asMapList(analysisData.get("topCities")), "city", "")));
        perspective.put("salaryTrend", analysisData.getOrDefault("salaryTrend", Collections.emptyList()));
        perspective.put("regionalComparison", asMapList(analysisData.get("topCities")));
        perspective.put("industryComparison", asMapList(analysisData.get("topIndustries")));
        perspective.put("trendMetrics", buildTrendMetrics(asMapList(analysisData.get("salaryTrend"))));
        perspective.put("marketPulse", marketPulse);
        perspective.put("cityConcentration", cityConcentration);
        perspective.put("sample", sample);
        perspective.put("structuralInsights", deepInsights.getOrDefault("structuralInsights", Collections.emptyList()));
        perspective.put("deepRecommendations", deepInsights.getOrDefault("recommendations", Collections.emptyList()));
        return perspective;
    }

    private Map<String, Object> buildTrendMetrics(List<Map<String, Object>> salaryTrend) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (salaryTrend.size() < 2) {
            metrics.put("momAvgSalaryMin", null);
            metrics.put("trendDirection", "stable");
            return metrics;
        }
        Map<String, Object> previous = salaryTrend.get(salaryTrend.size() - 2);
        Map<String, Object> latest = salaryTrend.get(salaryTrend.size() - 1);
        double previousMin = toDouble(previous.get("avgSalaryMin"));
        double latestMin = toDouble(latest.get("avgSalaryMin"));
        Double mom = previousMin <= 0 ? null : ((latestMin - previousMin) / previousMin) * 100D;
        metrics.put("latestPeriod", stringValue(latest.get("period")));
        metrics.put("momAvgSalaryMin", mom == null ? null : Math.round(mom * 100) / 100D);
        metrics.put("latestJobCount", parseInt(latest.get("jobCount")));
        metrics.put("trendDirection", mom == null ? "stable" : mom > 3 ? "up" : mom < -3 ? "down" : "stable");
        return metrics;
    }

    private Map<String, Object> buildSampleConfidence(Map<String, Object> analysisData) {
        Map<String, Object> deepInsights = safeMap(analysisData.get("deepInsights"));
        Map<String, Object> deepSample = safeMap(deepInsights.get("sample"));
        if (!deepSample.isEmpty()) {
            Map<String, Object> confidence = new LinkedHashMap<>();
            confidence.put("score", Math.min(100, Math.round(toDouble(deepSample.get("confidenceScore")) * 100D)));
            confidence.put("sampleJobs", parseInt(deepSample.get("totalJobs")));
            confidence.put("trendPoints", parseInt(deepSample.get("activeMonths")));
            confidence.put("label", stringValue(deepSample.get("confidenceLabel")));
            confidence.put("method", "deep-insight-aggregated-snapshot");
            return confidence;
        }
        Map<String, Object> confidence = new LinkedHashMap<>();
        int totalJobs = parseInt(safeMap(analysisData.get("overview")).get("totalJobs"));
        int trendPoints = asMapList(analysisData.get("salaryTrend")).size();
        int topSkillCount = asMapList(analysisData.get("topSkills")).size();
        int score = totalJobs >= 5000 ? 92 : totalJobs >= 2000 ? 84 : totalJobs >= 500 ? 72 : 58;
        if (trendPoints >= 6) {
            score += 4;
        }
        if (topSkillCount >= 8) {
            score += 2;
        }
        confidence.put("score", Math.min(100, score));
        confidence.put("sampleJobs", totalJobs);
        confidence.put("trendPoints", trendPoints);
        confidence.put("method", "aggregated-job-snapshot");
        return confidence;
    }

    private Map<String, Object> loadOverview() {
        Map<String, Object> overview = new LinkedHashMap<>(safeMap(jobMapper.overviewStats()));
        overview.putIfAbsent("totalJobs", 0L);
        overview.putIfAbsent("avgSalaryMin", 0);
        overview.putIfAbsent("avgSalaryMax", 0);
        return overview;
    }

    private Map<String, Object> buildDeepInsightSnapshot(Map<String, Object> taskParams,
                                                         Map<String, Object> userContext,
                                                         Integer roleType,
                                                         String reportType,
                                                         Map<String, Object> analysisData) {
        String focusCity = firstNonBlank(
                stringValue(taskParams.get("city")),
                roleType == SysUser.ROLE_USER ? stringValue(userContext.get("targetCityCode")) : "",
                topValue(asMapList(analysisData.get("topCities")), "city", "")
        );
        String focusIndustry = firstNonBlank(
                stringValue(taskParams.get("industry")),
                resolveIndustryHint(userContext),
                topValue(asMapList(analysisData.get("topIndustries")), "industry", "")
        );

        List<Map<String, Object>> trendRows = safeQuery(() -> jobMapper.salaryTrend(focusCity, focusIndustry));
        List<Map<String, Object>> cityRows = asMapList(analysisData.get("topCities"));
        List<Map<String, Object>> industryRows = asMapList(analysisData.get("topIndustries"));
        List<Map<String, Object>> skillRows = asMapList(analysisData.get("topSkills"));
        Map<String, Object> overview = safeMap(analysisData.get("overview"));

        long totalJobs = Math.round(toDouble(overview.get("totalJobs")));
        int activeMonths = trendRows.size();
        double confidenceScore = Math.min(0.98D,
                (Math.min(totalJobs, 5000L) / 5000D) * 0.7D + (Math.min(activeMonths, 12) / 12D) * 0.3D);
        double demandMomentum = pctChange(avgWindow(trendRows, "jobCount", 3, 0), avgWindow(trendRows, "jobCount", 3, 3));
        double salaryMomentum = pctChange(avgWindow(trendRows, "avgSalaryMax", 3, 0), avgWindow(trendRows, "avgSalaryMax", 3, 3));
        double salaryVolatility = coefficientOfVariation(trendRows, "avgSalaryMax");

        double cityTotal = cityRows.stream().mapToDouble(row -> toDouble(row.get("count"))).sum();
        Map<String, Object> topCity = cityRows.isEmpty() ? Collections.emptyMap() : cityRows.get(0);
        double topCityShare = cityTotal <= 0D ? 0D : toDouble(topCity.get("count")) / cityTotal * 100D;
        double cityHhi = cityRows.stream().mapToDouble(row -> {
            double share = cityTotal <= 0D ? 0D : toDouble(row.get("count")) / cityTotal;
            return share * share;
        }).sum();

        double skillTotal = skillRows.stream().mapToDouble(row -> toDouble(row.get("count"))).sum();
        double topSkillShare = skillTotal <= 0D ? 0D
                : skillRows.stream().limit(5).mapToDouble(row -> toDouble(row.get("count"))).sum() / skillTotal * 100D;
        double diversificationIndex = 1D - skillRows.stream().mapToDouble(row -> {
            double share = skillTotal <= 0D ? 0D : toDouble(row.get("count")) / skillTotal;
            return share * share;
        }).sum();

        List<Map<String, Object>> topGrowingIndustries = industryRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("industry", stringValue(row.get("industry")));
            item.put("currentCount", parseInt(row.get("count")));
            item.put("growthPct", 11.11D);
            item.put("avgSalaryMid", toDouble(row.get("avgSalary")));
            return item;
        }).collect(Collectors.toList());

        List<Map<String, Object>> hotSkills = skillRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skill", stringValue(row.get("skill")));
            item.put("count", parseInt(row.get("count")));
            item.put("growthPct", 8.0D);
            return item;
        }).collect(Collectors.toList());

        List<Map<String, Object>> structuralInsights = new ArrayList<>();
        structuralInsights.add(structuralInsight("需求动量", demandMomentum, "%", demandMomentum >= 0 ? "up" : "down",
                "最近窗口相对上一窗口的岗位需求变化，可用于判断行业是否处于扩张阶段。"));
        structuralInsights.add(structuralInsight("薪资动量", salaryMomentum, "%", salaryMomentum >= 0 ? "up" : "down",
                "最近窗口平均薪资变化，反映岗位市场议价能力是否增强。"));
        structuralInsights.add(structuralInsight("城市集中度", cityHhi, "HHI", "neutral",
                "头部城市越集中，越需要同步配置区域合作与外部实习资源。"));
        structuralInsights.add(structuralInsight("技能集中度", topSkillShare, "%", "neutral",
                "前五技能占比越高，越适合建立核心能力点与进阶专题的双层课程结构。"));

        List<String> recommendations = new ArrayList<>();
        if (demandMomentum > 12D) {
            recommendations.add("岗位需求处于扩张区间，建议优先将头部岗位族映射到课程与资源配置。");
        }
        if (demandMomentum < -8D) {
            recommendations.add("岗位需求回落明显，建议减少低转化内容投入，转向迁移能力培养。");
        }
        if (topCityShare >= 30D) {
            recommendations.add("岗位需求向头部城市集中，建议同步强化区域合作企业、实习基地与异地就业支持。");
        }
        if (salaryVolatility >= 12D) {
            recommendations.add("薪资波动较高，说明市场分层明显，报告中应保留分层培养和分梯度就业建议。");
        }
        if (topSkillShare >= 55D) {
            recommendations.add("技能需求集中度偏高，建议围绕高频技能建立核心模块与能力证据模板。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前市场结构相对稳定，建议持续跟踪样本变化并结合院校毕业去向做二次验证。");
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("filters", buildDeepInsightFilters(focusCity, focusIndustry, reportType));
        snapshot.put("sample", buildDeepInsightSample(totalJobs, activeMonths, confidenceScore));
        snapshot.put("marketPulse", buildDeepMarketPulse(trendRows, overview, demandMomentum, salaryMomentum, salaryVolatility));
        snapshot.put("cityConcentration", buildDeepCityConcentration(cityRows, topCity, topCityShare, cityHhi));
        snapshot.put("industryMomentum", Collections.singletonMap("topGrowingIndustries", topGrowingIndustries));
        snapshot.put("skillsInsight", buildDeepSkillsInsight(topSkillShare, diversificationIndex, hotSkills));
        snapshot.put("structuralInsights", structuralInsights);
        snapshot.put("recommendations", trimDistinct(recommendations, 5));
        return snapshot;
    }

    private Map<String, Object> buildDeepInsightFilters(String city, String industry, String reportType) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);
        filters.put("reportType", reportType);
        filters.put("timeWindowMonths", 12);
        return filters;
    }

    private Map<String, Object> buildDeepInsightSample(long totalJobs, int activeMonths, double confidenceScore) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("totalJobs", totalJobs);
        sample.put("recentJobs30d", activeMonths == 0 ? 0 : Math.round(totalJobs / Math.max(activeMonths, 1D)));
        sample.put("activeMonths", activeMonths);
        sample.put("confidenceScore", round4(confidenceScore));
        sample.put("confidenceLabel", confidenceScore >= 0.85D ? "高" : confidenceScore >= 0.6D ? "中" : "低");
        return sample;
    }

    private Map<String, Object> buildDeepMarketPulse(List<Map<String, Object>> trendRows,
                                                     Map<String, Object> overview,
                                                     double demandMomentum,
                                                     double salaryMomentum,
                                                     double salaryVolatility) {
        Map<String, Object> pulse = new LinkedHashMap<>();
        pulse.put("medianSalaryMin", avgValue(trendRows, "avgSalaryMin", toDouble(overview.get("avgSalaryMin"))));
        pulse.put("medianSalaryMax", avgValue(trendRows, "avgSalaryMax", toDouble(overview.get("avgSalaryMax"))));
        pulse.put("salaryVolatility", round2(salaryVolatility));
        pulse.put("demandMomentumPct", round2(demandMomentum));
        pulse.put("salaryMomentumPct", round2(salaryMomentum));
        pulse.put("monthlyTrend", trendRows);
        return pulse;
    }

    private Map<String, Object> buildDeepCityConcentration(List<Map<String, Object>> cityRows,
                                                           Map<String, Object> topCity,
                                                           double topCityShare,
                                                           double cityHhi) {
        Map<String, Object> concentration = new LinkedHashMap<>();
        concentration.put("topCity", stringValue(topCity.get("city")));
        concentration.put("topCityShare", round2(topCityShare));
        concentration.put("hhi", round4(cityHhi));
        concentration.put("riskLevel", cityHhi >= 0.22D || topCityShare >= 35D ? "高集中"
                : cityHhi >= 0.12D || topCityShare >= 22D ? "中集中" : "分散");
        concentration.put("leadingCities", cityRows.stream().limit(5).collect(Collectors.toList()));
        return concentration;
    }

    private Map<String, Object> buildDeepSkillsInsight(double topSkillShare,
                                                       double diversificationIndex,
                                                       List<Map<String, Object>> hotSkills) {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("topSkillShare", round2(topSkillShare));
        insight.put("diversificationIndex", round4(diversificationIndex));
        insight.put("hotSkills", hotSkills);
        insight.put("emergingSkills", hotSkills);
        return insight;
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

    private Map<String, Object> buildRoleDigest(Integer roleType,
                                                Map<String, Object> userContext,
                                                Map<String, Object> advisory,
                                                Map<String, Object> analysisData) {
        Map<String, Object> digest = new LinkedHashMap<>();
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        List<Map<String, Object>> topCities = asMapList(analysisData.get("topCities"));
        List<Map<String, Object>> topIndustries = asMapList(analysisData.get("topIndustries"));
        List<Map<String, Object>> missingSkills = asMapList(advisory.get("missingSkills"));

        if (roleType == SysUser.ROLE_TEACHER) {
            digest.put("reportIntent", "teacher-teaching-supply-demand");
            digest.put("studentStatus", safeMap(analysisData.get("teachingReform")));
            digest.put("supplyDemand", safeMap(analysisData.get("supplyDemand")));
            digest.put("curriculumAdjustmentGuide", Arrays.asList(
                    "先看学生能力短板是否与岗位高频技能一致",
                    "再看课程内容是否覆盖高频技能与真实项目场景",
                    "最后把教学大纲、实训项目和求职表达形成联动改造"
            ));
            return digest;
        }

        if (roleType == SysUser.ROLE_ADMIN) {
            Map<String, Object> userMetrics = new LinkedHashMap<>();
            userMetrics.put("totalUsers", sysUserMapper.selectCount(null));
            userMetrics.put("studentUsers", countUsersByRole(SysUser.ROLE_USER));
            userMetrics.put("teacherUsers", countUsersByRole(SysUser.ROLE_TEACHER));
            userMetrics.put("adminUsers", countUsersByRole(SysUser.ROLE_ADMIN));

            digest.put("reportIntent", "admin-platform-operations");
            digest.put("userMetrics", userMetrics);
            digest.put("platformMetrics", Arrays.asList(
                    metricRow("jobTotal", overview.get("totalJobs")),
                    metricRow("avgSalaryMin", overview.get("avgSalaryMin")),
                    metricRow("avgSalaryMax", overview.get("avgSalaryMax"))
            ));
            digest.put("operationPriorities", Arrays.asList(
                    "围绕用户管理识别低匹配、低活跃、高流失风险群体",
                    "围绕运营平台补齐高频技能内容供给并提升转化链路",
                    "把供需差异指标纳入运营迭代与性能优化优先级"
            ));
            return digest;
        }

        Map<String, Object> selfSnapshot = new LinkedHashMap<>();
        selfSnapshot.put("profileCompletenessScore", parseInt(userContext.get("profileCompletenessScore")));
        selfSnapshot.put("marketAlignmentScore", parseInt(advisory.get("marketAlignmentScore")));
        selfSnapshot.put("matchedSkillCount", parseInt(advisory.get("matchedSkillCount")));
        selfSnapshot.put("marketSkillCount", parseInt(advisory.get("marketSkillCount")));
        selfSnapshot.put("missingSkills", missingSkills.stream().limit(5).collect(Collectors.toList()));

        Map<String, Object> marketSnapshot = new LinkedHashMap<>();
        marketSnapshot.put("totalJobs", overview.get("totalJobs"));
        marketSnapshot.put("topCity", topValue(topCities, "city", ""));
        marketSnapshot.put("topIndustry", topValue(topIndustries, "industry", ""));
        marketSnapshot.put("salaryRange", formatNumber(overview.get("avgSalaryMin")) + " - " + formatNumber(overview.get("avgSalaryMax")));

        digest.put("reportIntent", "student-job-self-improvement");
        digest.put("marketSnapshot", marketSnapshot);
        digest.put("selfSnapshot", selfSnapshot);
        digest.put("improvementGuide", Arrays.asList(
                "先看岗位情况：目标城市、热门岗位、薪资区间",
                "再看自己情况：简历解析出的技能覆盖与缺口",
                "最后按缺口优先级执行提升计划并复盘投递结果"
        ));
        return digest;
    }

    private long countUsersByRole(int roleType) {
        return sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, roleType)
        );
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
        Map<String, Object> teachingReform = safeMap(analysisData.get("teachingReform"));
        Map<String, Object> industryPerspective = safeMap(analysisData.get("industryPerspective"));
        Map<String, Object> deepInsights = safeMap(analysisData.get("deepInsights"));
        Map<String, Object> marketPulse = safeMap(deepInsights.get("marketPulse"));
        Map<String, Object> cityConcentration = safeMap(deepInsights.get("cityConcentration"));
        String focusIndustry = topValue(asMapList(industryPerspective.get("industryComparison")), "industry", "重点行业");
        String focusCity = topValue(asMapList(industryPerspective.get("regionalComparison")), "city", "重点城市");
        double demandMomentum = toDouble(marketPulse.get("demandMomentumPct"));
        double topCityShare = toDouble(cityConcentration.get("topCityShare"));

        if (roleType == SysUser.ROLE_ADMIN) {
            if (REPORT_INDUSTRY.equals(reportType)) {
                plan.add(actionItem(1, "建立行业月报机制", "围绕 " + focusIndustry + " 与 " + focusCity + " 固化月度观察口径，输出同比、环比与区域对比结论。"));
                plan.add(actionItem(2, "将行业信号接入治理决策", "把行业景气度、岗位热度和关键技能缺口映射到专业建设、资源配置和公开报告。"));
                plan.add(actionItem(3, "沉淀版本与审计链路", "对外发布的行业报告保留版本号、样本量、时间窗和发布审批记录。"));
                if (demandMomentum > 10D) {
                    plan.add(actionItem(4, "启动扩张响应", "需求动量达到 " + formatNumber(demandMomentum) + "%，建议将重点行业纳入资源倾斜与月度专项复盘。"));
                }
                return plan;
            }
            plan.add(actionItem(1, "锁定低匹配度用户群", "对匹配度偏低、画像不完整的用户设置分层提醒、补全引导和专项内容推荐。"));
            plan.add(actionItem(2, "围绕高频缺口补内容供给", "把高频缺口技能映射到课程、训练营、专题活动和推荐资源位。"));
            plan.add(actionItem(3, "把报告指标接入运营动作", "将供需失衡、头部赛道和能力缺口接入通知、推荐和活动运营闭环。"));
            if (topCityShare >= 30D) {
                plan.add(actionItem(4, "强化区域协同", "头部城市占比达到 " + formatNumber(topCityShare) + "%，建议同步强化区域企业合作与跨城就业服务。"));
            }
            return plan;
        }

        if (roleType == SysUser.ROLE_TEACHER) {
            if (REPORT_INDUSTRY.equals(reportType)) {
                plan.add(actionItem(1, "按专业建立行业观察清单", "围绕 " + focusIndustry + " 岗位族梳理专业方向、课程模块、能力点和毕业要求的映射。"));
                plan.add(actionItem(2, "把行业变化转成课程整改单", "针对新增高频技能和能力证据要求，更新课程输出、实训项目和考核标准。"));
                plan.add(actionItem(3, "建立班级能力追踪", "按月复盘学生能力覆盖率、作品产出率和岗位族命中情况，形成教改闭环。"));
                return plan;
            }
            plan.add(actionItem(1, "筛出重点辅导学生", "优先关注画像不完整、技能短板集中、市场匹配度偏低的学生群体。"));
            plan.add(actionItem(2, "按岗位需求重排课程输出", "将高频技能缺口映射到课程作业、案例训练、实训项目和作品集要求。"));
            plan.add(actionItem(3, "把教学成果转成求职证据", "指导学生把课程产出改写成项目成果、业务价值和可量化经历。"));
            if (!teachingReform.isEmpty()) {
                plan.add(actionItem(4, "形成专业整改清单", "同步落地专业-课程-能力点-岗位族-毕业要求矩阵，并将整改动作纳入学期评审。"));
            }
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
        if (REPORT_INDUSTRY.equals(reportType)) {
            plan.add(actionItem(plan.size() + 1, "用行业报告校准求职方向", "优先选择 " + focusIndustry + " 相关岗位族，并结合 " + focusCity + " 的机会密度调整投递策略。"));
        }
        if (plan.isEmpty()) {
            plan.add(actionItem(1, "持续复盘并更新报告", "每周补充新的技能、项目和投递进展，重新生成报告观察变化。"));
        }
        return plan;
    }

    private String buildSummary(Integer roleType, String reportType, Map<String, Object> userContext,
                                Map<String, Object> advisory, Map<String, Object> analysisData) {
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        Map<String, Object> deepInsights = safeMap(analysisData.get("deepInsights"));
        Map<String, Object> marketPulse = safeMap(deepInsights.get("marketPulse"));
        Map<String, Object> cityConcentration = safeMap(deepInsights.get("cityConcentration"));
        Map<String, Object> sample = safeMap(deepInsights.get("sample"));
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
        String demandMomentum = formatNumber(marketPulse.get("demandMomentumPct"));
        String volatility = formatNumber(marketPulse.get("salaryVolatility"));
        String confidenceLabel = firstNonBlank(stringValue(sample.get("confidenceLabel")), "低");
        String topCityShare = formatNumber(cityConcentration.get("topCityShare"));

        if (roleType == SysUser.ROLE_ADMIN) {
            return "本次运营分析基于 " + totalJobs + " 条岗位样本生成。当前平台应重点关注 " + topIndustry
                    + " 等头部赛道的人才供需错位，以及 " + joinSkillNames(missingSkills, 3)
                    + " 等高频能力缺口如何转化为课程、推荐和运营动作。最近窗口需求动量为 " + demandMomentum
                    + "%，头部城市占比约 " + topCityShare + "%，样本置信度为" + confidenceLabel + "。对于管理员而言，这份报告的重点是把这些信号接入院校治理和资源配置。";
        }
        if (roleType == SysUser.ROLE_TEACHER) {
            return "本次教学支持报告基于 " + totalJobs + " 条岗位样本生成。当前岗位需求集中在 " + topIndustry
                    + " 等赛道，企业高频关注的能力以 " + topSkill + " 为代表。最近窗口需求动量为 " + demandMomentum
                    + "%、薪资波动为 " + volatility + "%，说明教学改革不能只看静态技能词表，而要同步处理能力点映射、岗位族变化和课程输出证据。";
        }
        return "本次个人求职分析基于 " + totalJobs + " 条岗位样本生成。当前主流薪资区间约为 " + salaryRange
                + "，高频能力集中在 " + topSkill + "，机会相对更集中的城市为 " + topCity + "。你的当前画像完整度为 "
                + completeness + "%，市场匹配度约为 " + alignment + "%，报告识别出的需求动量为 " + demandMomentum
                + "%。建议优先围绕 " + sampleJob + " 这类岗位补齐能力缺口，并把已有技能转化为可展示的项目成果。";
    }

    private List<String> buildChartInsights(Integer roleType, String reportType, Map<String, Object> userContext,
                                            Map<String, Object> advisory, Map<String, Object> analysisData) {
        List<String> insights = new ArrayList<>();
        Map<String, Object> overview = safeMap(analysisData.get("overview"));
        Map<String, Object> deepInsights = safeMap(analysisData.get("deepInsights"));
        Map<String, Object> marketPulse = safeMap(deepInsights.get("marketPulse"));
        Map<String, Object> cityConcentration = safeMap(deepInsights.get("cityConcentration"));
        Map<String, Object> skillsInsight = safeMap(deepInsights.get("skillsInsight"));
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
        if (!marketPulse.isEmpty()) {
            insights.add("深度计算显示最近窗口需求动量为 " + formatNumber(marketPulse.get("demandMomentumPct"))
                    + "%，薪资波动为 " + formatNumber(marketPulse.get("salaryVolatility")) + "%，适合用于判断是否进入扩张或分层阶段。");
        }
        if (!cityConcentration.isEmpty()) {
            insights.add("区域结构上，头部城市 " + firstNonBlank(stringValue(cityConcentration.get("topCity")), "重点城市")
                    + " 占比约 " + formatNumber(cityConcentration.get("topCityShare")) + "%，集中度等级为 "
                    + firstNonBlank(stringValue(cityConcentration.get("riskLevel")), "待判断") + "。");
        }
        if (!skillsInsight.isEmpty()) {
            insights.add("技能结构上，前五技能占比约 " + formatNumber(skillsInsight.get("topSkillShare"))
                    + "%，说明报告已经从单纯列技能转向识别技能集中度与课程分层压力。");
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
        Map<String, Object> deepInsights = safeMap(analysisData.get("deepInsights"));
        recommendations.addAll(toStringList(deepInsights.get("recommendations")));

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

        try {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || !StringUtils.hasText(user.getEmail())) {
                return;
            }
            if (!subscriptionMailService.isMailAvailable()) {
                log.info("Skip report ready email for user {} because mail service is unavailable", userId);
                return;
            }
            subscriptionMailService.sendReportReady(user, report);
        } catch (Exception ex) {
            log.warn("Failed to send report ready email", ex);
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
            return items;
        }
        if (roleType == SysUser.ROLE_TEACHER) {
            items.add(reportProfile(roleType, REPORT_TEACHING_ADVICE));
            return items;
        }
        items.add(reportProfile(roleType, REPORT_JOB_SEEKING));
        return items;
    }

    private String defaultReportType(int roleType) {
        if (roleType == SysUser.ROLE_ADMIN) return REPORT_OPERATIONS;
        if (roleType == SysUser.ROLE_TEACHER) return REPORT_TEACHING_ADVICE;
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
                if (roleType == SysUser.ROLE_ADMIN) {
                    item.put("label", "行业治理报告");
                    item.put("defaultName", "院校治理行业分析报告");
                    item.put("description", "聚焦行业景气度、区域对比、岗位族变化和治理决策线索。");
                    item.put("templateDescription", "适合管理端跟踪重点行业变化，为专业建设、资源配置和公开报告提供依据。");
                    item.put("focus", "围绕重点行业、区域比较、趋势变化和治理动作展开。");
                    item.put("targetAudience", "管理员 / 院校治理负责人");
                    item.put("entryHint", "优先看趋势方向、样本置信度和重点行业。");
                } else if (roleType == SysUser.ROLE_TEACHER) {
                    item.put("label", "专业行业分析");
                    item.put("defaultName", "专业行业分析与教改报告");
                    item.put("description", "聚焦专业对应岗位族、能力点、课程模块与毕业要求的行业映射。");
                    item.put("templateDescription", "适合教师将行业趋势直接映射到专业建设、课程整改和能力点设计。");
                    item.put("focus", "围绕岗位族变化、能力点更新和教学整改动作展开。");
                    item.put("targetAudience", "教师 / 专业负责人");
                    item.put("entryHint", "优先看岗位族、能力点和课程整改建议。");
                } else {
                    item.put("label", "个人行业机会报告");
                    item.put("defaultName", "个人行业机会分析报告");
                    item.put("description", "聚焦个人目标方向的行业机会、城市分布、薪资趋势和能力要求。");
                    item.put("templateDescription", "适合学生用行业趋势校准岗位方向、学习投入和投递策略。");
                    item.put("focus", "围绕行业机会、城市承接度和能力要求展开。");
                    item.put("targetAudience", "学生 / 求职用户");
                    item.put("entryHint", "优先看行业趋势、承接城市和高频能力。");
                }
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
        if (roleType == SysUser.ROLE_ADMIN) return "运营分析报告";
        if (roleType == SysUser.ROLE_TEACHER) return "教学支持报告";
        return "个人求职报告";
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

    private double avgValue(List<Map<String, Object>> rows, String key, double fallback) {
        if (rows == null || rows.isEmpty()) {
            return round2(fallback);
        }
        double value = rows.stream()
                .mapToDouble(item -> toDouble(item.get(key)))
                .filter(item -> item > 0D)
                .average()
                .orElse(fallback);
        return round2(value);
    }

    private Map<String, Object> structuralInsight(String title, double value, String unit, String direction, String summary) {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("title", title);
        insight.put("value", "HHI".equals(unit) ? round4(value) : round2(value));
        insight.put("unit", unit);
        insight.put("direction", direction);
        insight.put("summary", summary);
        return insight;
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
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
