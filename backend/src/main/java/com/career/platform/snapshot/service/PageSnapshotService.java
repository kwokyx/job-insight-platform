package com.career.platform.snapshot.service;

import com.career.platform.crawl.entity.CrawlTask;
import com.career.platform.crawl.mapper.CrawlTaskMapper;
import com.career.platform.crawl.service.DataQualityService;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.system.entity.OperationLog;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.OperationLogMapper;
import com.career.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class PageSnapshotService {

    public static final String SNAPSHOT_MARKET_OVERVIEW = "MARKET_OVERVIEW";
    public static final String SNAPSHOT_MARKET_SKILLS = "MARKET_SKILLS";
    public static final String SNAPSHOT_HOME_HOT_JOBS = "HOME_HOT_JOBS";
    public static final String SNAPSHOT_INSIGHTS_SALARY_TREND = "INSIGHTS_SALARY_TREND";
    public static final String SNAPSHOT_INSIGHTS_WELFARE = "INSIGHTS_WELFARE";
    public static final String SNAPSHOT_INSIGHTS_COMPANY_SIZE = "INSIGHTS_COMPANY_SIZE";
    public static final String SNAPSHOT_INSIGHTS_FINANCE_STAGE = "INSIGHTS_FINANCE_STAGE";
    public static final String SNAPSHOT_ADMIN_OPERATIONS = "ADMIN_OPERATIONS";

    private static final Logger log = LoggerFactory.getLogger(PageSnapshotService.class);

    private static final Map<String, String> SNAPSHOT_LABELS;
    private static final List<String> DEFAULT_SNAPSHOT_CODES = Collections.unmodifiableList(Arrays.asList(
            SNAPSHOT_MARKET_OVERVIEW,
            SNAPSHOT_MARKET_SKILLS,
            SNAPSHOT_HOME_HOT_JOBS,
            SNAPSHOT_INSIGHTS_SALARY_TREND,
            SNAPSHOT_INSIGHTS_WELFARE,
            SNAPSHOT_INSIGHTS_COMPANY_SIZE,
            SNAPSHOT_INSIGHTS_FINANCE_STAGE,
            SNAPSHOT_ADMIN_OPERATIONS
    ));

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(SNAPSHOT_MARKET_OVERVIEW, "首页与洞察概览");
        labels.put(SNAPSHOT_MARKET_SKILLS, "技能热度榜单");
        labels.put(SNAPSHOT_HOME_HOT_JOBS, "首页热门岗位");
        labels.put(SNAPSHOT_INSIGHTS_SALARY_TREND, "数据分析薪资趋势");
        labels.put(SNAPSHOT_INSIGHTS_WELFARE, "数据分析福利分布");
        labels.put(SNAPSHOT_INSIGHTS_COMPANY_SIZE, "数据分析公司规模");
        labels.put(SNAPSHOT_INSIGHTS_FINANCE_STAGE, "数据分析融资阶段");
        labels.put(SNAPSHOT_ADMIN_OPERATIONS, "管理员运营面板");
        SNAPSHOT_LABELS = Collections.unmodifiableMap(labels);
    }

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final JobPostingMapper jobMapper;
    private final SysUserMapper userMapper;
    private final AnalysisReportMapper reportMapper;
    private final OperationLogMapper logMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final DataQualityService dataQualityService;

    public PageSnapshotService(JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               JobPostingMapper jobMapper,
                               SysUserMapper userMapper,
                               AnalysisReportMapper reportMapper,
                               OperationLogMapper logMapper,
                               CrawlTaskMapper crawlTaskMapper,
                               DataQualityService dataQualityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.reportMapper = reportMapper;
        this.logMapper = logMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.dataQualityService = dataQualityService;
    }

    public Map<String, Object> getMarketOverview() {
        return getOrBuildMap(SNAPSHOT_MARKET_OVERVIEW, this::buildMarketOverviewSnapshot);
    }

    public List<Map<String, Object>> getMarketSkills(int limit) {
        return sliceRows(getOrBuildList(SNAPSHOT_MARKET_SKILLS, this::buildMarketSkillsSnapshot), limit);
    }

    public List<Map<String, Object>> getHomeHotJobs(int limit) {
        return sliceRows(getOrBuildList(SNAPSHOT_HOME_HOT_JOBS, this::buildHotJobsSnapshot), limit);
    }

    public Map<String, Object> getSalaryTrend(String city, String industry) {
        if (isBlank(city) && isBlank(industry)) {
            return getOrBuildMap(SNAPSHOT_INSIGHTS_SALARY_TREND, this::buildSalaryTrendSnapshot);
        }
        return buildSalaryTrendPayload(city, industry);
    }

    public List<Map<String, Object>> getWelfareDistribution(int limit) {
        return sliceRows(getOrBuildList(SNAPSHOT_INSIGHTS_WELFARE, this::buildWelfareSnapshot), limit);
    }

    public List<Map<String, Object>> getCompanySizeDistribution() {
        return getOrBuildList(SNAPSHOT_INSIGHTS_COMPANY_SIZE, this::buildCompanySizeSnapshot);
    }

    public List<Map<String, Object>> getFinanceStageDistribution() {
        return getOrBuildList(SNAPSHOT_INSIGHTS_FINANCE_STAGE, this::buildFinanceStageSnapshot);
    }

    public Map<String, Object> getAdminOperations() {
        return getOrBuildMap(SNAPSHOT_ADMIN_OPERATIONS, this::buildAdminOperationsSnapshot);
    }

    public Map<String, Object> refreshSnapshots(List<String> requestedCodes, String trigger) {
        List<String> targetCodes = normalizeCodes(requestedCodes);
        List<Map<String, Object>> refreshed = new ArrayList<>();
        for (String code : targetCodes) {
            Object payload = buildPayload(code);
            if (payload == null) {
                continue;
            }
            storeSnapshot(code, payload, trigger);
            refreshed.add(buildStatusEntry(code, trigger, LocalDateTime.now()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trigger", trigger);
        result.put("pages", refreshed);
        result.put("nextScheduledAt", nextScheduledAt().toString());
        result.put("refreshedAt", LocalDateTime.now().toString());
        return result;
    }

    public Map<String, Object> snapshotStatus() {
        Map<String, Map<String, Object>> statusByCode = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT page_code, page_name, refresh_trigger, refreshed_at FROM ads_page_snapshot"
            );
            for (Map<String, Object> row : rows) {
                String code = String.valueOf(row.get("page_code"));
                statusByCode.put(code, buildStatusEntry(
                        code,
                        readString(row.get("refresh_trigger")),
                        readDateTime(row.get("refreshed_at")),
                        readString(row.get("page_name"))
                ));
            }
        } catch (DataAccessException e) {
            log.warn("Load snapshot status failed, fallback to empty status: {}", e.getMessage());
        }

        List<Map<String, Object>> pages = new ArrayList<>();
        for (String code : DEFAULT_SNAPSHOT_CODES) {
            pages.add(statusByCode.getOrDefault(code, buildStatusEntry(code, "PENDING", null)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pages", pages);
        result.put("nextScheduledAt", nextScheduledAt().toString());
        result.put("schedule", "每天 04:00");
        return result;
    }

    private Map<String, Object> getOrBuildMap(String code, Supplier<Map<String, Object>> builder) {
        Object cached = loadSnapshotPayload(code);
        if (cached instanceof Map) {
            return castMap(cached);
        }
        Map<String, Object> payload = builder.get();
        storeSnapshot(code, payload, "ON_DEMAND");
        return payload;
    }

    private List<Map<String, Object>> getOrBuildList(String code, Supplier<List<Map<String, Object>>> builder) {
        Object cached = loadSnapshotPayload(code);
        if (cached instanceof List) {
            return castList(cached);
        }
        List<Map<String, Object>> payload = builder.get();
        storeSnapshot(code, payload, "ON_DEMAND");
        return payload;
    }

    private Object loadSnapshotPayload(String code) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT payload_json FROM ads_page_snapshot WHERE page_code = ? LIMIT 1",
                    code
            );
            if (rows.isEmpty()) {
                return null;
            }
            Object raw = rows.get(0).get("payload_json");
            if (raw == null) {
                return null;
            }
            return objectMapper.readValue(String.valueOf(raw), Object.class);
        } catch (Exception e) {
            log.warn("Load snapshot {} failed, fallback to live query: {}", code, e.getMessage());
            return null;
        }
    }

    private void storeSnapshot(String code, Object payload, String trigger) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ads_page_snapshot(page_code, page_name, payload_json, refresh_trigger, refreshed_at, created_at, updated_at) " +
                            "VALUES (?, ?, CAST(? AS JSON), ?, NOW(), NOW(), NOW()) " +
                            "ON DUPLICATE KEY UPDATE page_name = VALUES(page_name), payload_json = VALUES(payload_json), " +
                            "refresh_trigger = VALUES(refresh_trigger), refreshed_at = VALUES(refreshed_at), updated_at = NOW()",
                    code,
                    snapshotLabel(code),
                    objectMapper.writeValueAsString(payload),
                    trigger
            );
        } catch (Exception e) {
            log.warn("Store snapshot {} failed: {}", code, e.getMessage());
        }
    }

    private Object buildPayload(String code) {
        switch (code) {
            case SNAPSHOT_MARKET_OVERVIEW:
                return buildMarketOverviewSnapshot();
            case SNAPSHOT_MARKET_SKILLS:
                return buildMarketSkillsSnapshot();
            case SNAPSHOT_HOME_HOT_JOBS:
                return buildHotJobsSnapshot();
            case SNAPSHOT_INSIGHTS_SALARY_TREND:
                return buildSalaryTrendSnapshot();
            case SNAPSHOT_INSIGHTS_WELFARE:
                return buildWelfareSnapshot();
            case SNAPSHOT_INSIGHTS_COMPANY_SIZE:
                return buildCompanySizeSnapshot();
            case SNAPSHOT_INSIGHTS_FINANCE_STAGE:
                return buildFinanceStageSnapshot();
            case SNAPSHOT_ADMIN_OPERATIONS:
                return buildAdminOperationsSnapshot();
            default:
                return null;
        }
    }

    private Map<String, Object> buildMarketOverviewSnapshot() {
        Map<String, Object> stats = new LinkedHashMap<>(safeMap(jobMapper.overviewStats()));
        stats.put("totalJobs", jobMapper.selectCount(null));
        stats.put("topCities", safeList(jobMapper.aggregateByCity(10)));
        stats.put("topIndustries", safeList(jobMapper.aggregateByIndustry(10)));
        stats.put("topSkills", safeList(jobMapper.topSkills(10)));
        stats.put("educationDistribution", safeList(jobMapper.aggregateByEducation()));
        stats.put("experienceDistribution", safeList(jobMapper.aggregateByExperience()));
        return stats;
    }

    private List<Map<String, Object>> buildMarketSkillsSnapshot() {
        return safeList(jobMapper.topSkills(50));
    }

    private List<Map<String, Object>> buildHotJobsSnapshot() {
        return safeList(jobMapper.hotJobs(20));
    }

    private Map<String, Object> buildSalaryTrendSnapshot() {
        return buildSalaryTrendPayload(null, null);
    }

    private Map<String, Object> buildSalaryTrendPayload(String city, String industry) {
        List<Map<String, Object>> rows = safeList(jobMapper.salaryTrend(city, industry));
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);

        Map<String, Object> seriesMin = new LinkedHashMap<>();
        seriesMin.put("name", "avgSalaryMin");
        seriesMin.put("data", collectColumn(rows, "avgSalaryMin"));

        Map<String, Object> seriesMax = new LinkedHashMap<>();
        seriesMax.put("name", "avgSalaryMax");
        seriesMax.put("data", collectColumn(rows, "avgSalaryMax"));

        Map<String, Object> seriesCount = new LinkedHashMap<>();
        seriesCount.put("name", "jobCount");
        seriesCount.put("data", collectColumn(rows, "jobCount"));

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("chartType", "line");
        chart.put("xAxis", collectColumn(rows, "period"));
        chart.put("series", Arrays.asList(seriesMin, seriesMax, seriesCount));
        chart.put("filters", filters);
        chart.put("data", rows);
        return chart;
    }

    private List<Map<String, Object>> buildWelfareSnapshot() {
        return safeList(jobMapper.aggregateByWelfare(20));
    }

    private List<Map<String, Object>> buildCompanySizeSnapshot() {
        return safeList(jobMapper.aggregateByCompanySize());
    }

    private List<Map<String, Object>> buildFinanceStageSnapshot() {
        return safeList(jobMapper.aggregateByFinanceStage());
    }

    private Map<String, Object> buildAdminOperationsSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();

        long totalUsers = userMapper.selectCount(null);
        long newUsersToday = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().ge(SysUser::getCreatedAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN))
        );
        long studentCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, 0));
        long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, 1));
        long teacherCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, 2));
        long bannedCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 0));
        long activeToday = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().ge(SysUser::getLastLoginAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN))
        );

        data.put("totalUsers", totalUsers);
        data.put("newUsersToday", newUsersToday);
        data.put("studentCount", studentCount);
        data.put("adminCount", adminCount);
        data.put("teacherCount", teacherCount);
        data.put("bannedCount", bannedCount);
        data.put("activeToday", activeToday);
        data.put("totalJobs", jobMapper.selectCount(null));
        data.put("newJobs7d", jobMapper.countJobsSince(LocalDate.now().minusDays(7)));
        data.put("totalReports", reportMapper.selectCount(null));
        data.put("registrationTrend", safeList(jobMapper.userRegistrationTrend(LocalDate.now().minusDays(30))));

        List<OperationLog> recentLogs = logMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .orderByDesc(OperationLog::getCreatedAt)
                        .last("LIMIT 10")
        );
        data.put("recentLogs", recentLogs);

        List<CrawlTask> tasks = crawlTaskMapper.selectList(
                new LambdaQueryWrapper<CrawlTask>()
                        .orderByDesc(CrawlTask::getCreateTime)
                        .last("LIMIT 20")
        );
        long runningTasks = tasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), 1))
                .count();
        Map<String, Object> crawlSummary = new LinkedHashMap<>();
        crawlSummary.put("totalTasks", tasks.size());
        crawlSummary.put("runningTasks", runningTasks);
        crawlSummary.put("quality", dataQualityService.getQualityReport());
        data.put("crawlSummary", crawlSummary);
        return data;
    }

    private List<String> normalizeCodes(List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return DEFAULT_SNAPSHOT_CODES;
        }
        List<String> normalized = new ArrayList<>();
        for (String code : requestedCodes) {
            if (SNAPSHOT_LABELS.containsKey(code)) {
                normalized.add(code);
            }
        }
        return normalized.isEmpty() ? DEFAULT_SNAPSHOT_CODES : normalized;
    }

    private List<Object> collectColumn(List<Map<String, Object>> rows, String key) {
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            values.add(row.get(key));
        }
        return values;
    }

    private Map<String, Object> buildStatusEntry(String code, String trigger, LocalDateTime refreshedAt) {
        return buildStatusEntry(code, trigger, refreshedAt, snapshotLabel(code));
    }

    private Map<String, Object> buildStatusEntry(String code, String trigger, LocalDateTime refreshedAt, String pageName) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pageCode", code);
        item.put("pageName", pageName);
        item.put("refreshTrigger", trigger);
        item.put("refreshedAt", refreshedAt == null ? null : refreshedAt.toString());
        return item;
    }

    private String snapshotLabel(String code) {
        return SNAPSHOT_LABELS.getOrDefault(code, code);
    }

    private LocalDateTime nextScheduledAt() {
        LocalDateTime next = LocalDateTime.of(LocalDate.now(), LocalTime.of(4, 0));
        return LocalDateTime.now().isBefore(next) ? next : next.plusDays(1);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime readDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    private Map<String, Object> castMap(Object payload) {
        return objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }

    private List<Map<String, Object>> castList(Object payload) {
        return objectMapper.convertValue(payload, new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Map<String, Object>> sliceRows(List<Map<String, Object>> rows, int limit) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, Math.min(limit, rows.size()));
        return new ArrayList<>(rows.subList(0, safeLimit));
    }
}
