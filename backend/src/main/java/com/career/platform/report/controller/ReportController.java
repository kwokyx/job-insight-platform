package com.career.platform.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.entity.TeacherMaterialAsset;
import com.career.platform.platform.mapper.TeacherMaterialAssetMapper;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.entity.ReportSchedule;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.mapper.ReportScheduleMapper;
import com.career.platform.report.service.PdfExportService;
import com.career.platform.report.service.ReportFailureReason;
import com.career.platform.report.service.ReportGenerationService;
import com.career.platform.report.service.SensitiveDataMaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.SysUserMapper;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Analysis Reports", description = "Report generation, task status, listing, and export")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private static final String STUDENT_RECOMMEND_LAST_RUN_KEY_PREFIX = "recommend:last_run:user:";
    private static final String REPORT_DATA_VERSION = "2026-04-22-report-v1";

    private final AnalysisReportMapper reportMapper;
    private final AnalysisTaskMapper taskMapper;
    private final ReportScheduleMapper reportScheduleMapper;
    private final ObjectMapper objectMapper;
    private final ReportGenerationService reportGenerationService;
    private final PdfExportService pdfExportService;
    private final UserInsightService userInsightService;
    private final SensitiveDataMaskingService sensitiveDataMaskingService;
    private final CurriculumMapper curriculumMapper;
    private final TeacherMaterialAssetMapper teacherMaterialAssetMapper;
    private final UserProfileMapper userProfileMapper;
    private final JobPostingMapper jobPostingMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;

    public ReportController(AnalysisReportMapper reportMapper, AnalysisTaskMapper taskMapper,
                            ReportScheduleMapper reportScheduleMapper, ObjectMapper objectMapper,
                            ReportGenerationService reportGenerationService, PdfExportService pdfExportService,
                            UserInsightService userInsightService, SensitiveDataMaskingService sensitiveDataMaskingService,
                            CurriculumMapper curriculumMapper, TeacherMaterialAssetMapper teacherMaterialAssetMapper,
                            UserProfileMapper userProfileMapper, JobPostingMapper jobPostingMapper,
                            SysUserMapper sysUserMapper, StringRedisTemplate redisTemplate) {
        this.reportMapper = reportMapper;
        this.taskMapper = taskMapper;
        this.reportScheduleMapper = reportScheduleMapper;
        this.objectMapper = objectMapper;
        this.reportGenerationService = reportGenerationService;
        this.pdfExportService = pdfExportService;
        this.userInsightService = userInsightService;
        this.sensitiveDataMaskingService = sensitiveDataMaskingService;
        this.curriculumMapper = curriculumMapper;
        this.teacherMaterialAssetMapper = teacherMaterialAssetMapper;
        this.userProfileMapper = userProfileMapper;
        this.jobPostingMapper = jobPostingMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
    }

    @Operation(summary = "Get current role report-center meta")
    @GetMapping("/meta")
    public R<?> reportCenterMeta() {
        return R.ok(reportGenerationService.buildReportCenterMeta(getCurrentRoleType()));
    }

    @Operation(summary = "Get report and AI readiness for current role")
    @GetMapping("/readiness")
    public R<?> reportReadiness(@RequestParam(required = false) String major) {
        Long userId = requireCurrentUserId();
        Integer roleType = getCurrentRoleType();

        List<Map<String, Object>> missingRequirements = new ArrayList<>();
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            collectTeacherMissingRequirements(userId, major, missingRequirements);
        } else if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            collectAdminMissingRequirements(missingRequirements);
        } else {
            collectStudentMissingRequirements(userId, missingRequirements);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("roleType", roleType == null ? SysUser.ROLE_USER : roleType);
        payload.put("roleLabel", roleLabel(roleType));
        payload.put("status", missingRequirements.isEmpty() ? "ready" : "missing_input");
        payload.put("ready", missingRequirements.isEmpty());
        payload.put("missingRequirements", missingRequirements);

        Map<String, Object> primaryAction = missingRequirements.isEmpty()
                ? actionItem("/report-center", "生成角色专属报告", "前置数据已准备完成，可直接生成报告。")
                : firstAction(missingRequirements);
        payload.put("primaryAction", primaryAction);
        payload.put("assistant", buildAssistantReadiness(missingRequirements, primaryAction));
        return R.ok(payload);
    }

    @Operation(summary = "Get private report list")
    @GetMapping
    public R<?> listReports(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = requireCurrentUserId();
        Integer roleType = getCurrentRoleType();

        LambdaQueryWrapper<AnalysisReport> wrapper = new LambdaQueryWrapper<>();
        if (roleType == null || roleType != 1) {
            wrapper.eq(AnalysisReport::getGeneratedBy, userId);
        }
        wrapper.orderByDesc(AnalysisReport::getGeneratedAt);

        IPage<AnalysisReport> result = reportMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(buildReportSummaries(result.getRecords()), result.getTotal(), page, pageSize);
    }

    @Operation(summary = "Get public report list")
    @GetMapping("/public")
    public R<?> publicReports(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<AnalysisReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisReport::getIsPublic, 1).orderByDesc(AnalysisReport::getGeneratedAt);
        IPage<AnalysisReport> result = reportMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(buildReportSummaries(result.getRecords()), result.getTotal(), page, pageSize);
    }

    @Operation(summary = "Get publication review queue")
    @GetMapping("/publication/queue")
    public R<?> publicationQueue(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        requireAdminRole();
        IPage<AnalysisReport> result = reportMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<AnalysisReport>().orderByDesc(AnalysisReport::getGeneratedAt)
        );
        return R.page(buildReportSummaries(result.getRecords()), result.getTotal(), page, pageSize);
    }

    public static class GenerateRequest {
        @NotBlank(message = "reportName is required")
        private String reportName;

        @NotBlank(message = "reportType is required")
        private String reportType;

        private Map<String, Object> params = Collections.emptyMap();

        public String getReportName() { return reportName; }
        public void setReportName(String reportName) { this.reportName = reportName; }
        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }

    public static class ScheduleRequest {
        private String scheduleName;

        @NotBlank(message = "reportType is required")
        private String reportType;

        @NotBlank(message = "cronExpr is required")
        private String cronExpr;

        private Map<String, Object> params = Collections.emptyMap();

        public String getScheduleName() { return scheduleName; }
        public void setScheduleName(String scheduleName) { this.scheduleName = scheduleName; }
        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }
        public String getCronExpr() { return cronExpr; }
        public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }

    @Log("Generate analysis report")
    @Operation(summary = "Create report generation task")
    @PostMapping("/generate")
    public R<?> generateReport(@Valid @RequestBody GenerateRequest req) {
        Long userId = requireCurrentUserId();
        Integer roleType = getCurrentRoleType();
        validateReportType(roleType, req.getReportType());

        String normalizedType = normalizeType(req.getReportType());
        String normalizedName = normalizeReportName(req.getReportName(), normalizedType, roleType);
        Map<String, Object> params = req.getParams() == null ? new HashMap<>() : new HashMap<>(req.getParams());
        params.put("targetRoleType", roleType == null ? 0 : roleType);
        validateTeacherReportPrerequisites(userId, roleType, normalizedType, params);

        AnalysisTask task = new AnalysisTask();
        task.setTaskName(normalizedName);
        task.setTaskType(normalizedType);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreatedBy(userId);
        task.setCreatedAt(LocalDateTime.now());
        try {
            task.setParams(objectMapper.writeValueAsString(params));
        } catch (Exception ignored) {
            task.setParams("{}");
        }

        taskMapper.insert(task);
        reportGenerationService.executeReportGeneration(task.getId(), normalizedType, normalizedName, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        return R.ok("Report generation task created", data);
    }

    @Log("Create report schedule")
    @Operation(summary = "Create scheduled report plan")
    @PostMapping("/schedule")
    public R<?> createSchedule(@Valid @RequestBody ScheduleRequest req) {
        Long userId = requireCurrentUserId();
        Integer roleType = getCurrentRoleType();
        validateReportType(roleType, req.getReportType());

        CronExpression expression;
        try {
            expression = CronExpression.parse(req.getCronExpr());
        } catch (Exception e) {
            throw BusinessException.of(400, "Invalid cron expression");
        }

        ReportSchedule schedule = new ReportSchedule();
        schedule.setScheduleName(normalizeReportName(req.getScheduleName(), req.getReportType(), roleType));
        schedule.setReportType(normalizeType(req.getReportType()));
        schedule.setCronExpr(req.getCronExpr());
        schedule.setIsActive(1);
        schedule.setCreatedBy(userId);
        schedule.setCreatedAt(LocalDateTime.now());

        Map<String, Object> params = req.getParams() == null ? new HashMap<>() : new HashMap<>(req.getParams());
        params.put("targetRoleType", roleType == null ? 0 : roleType);
        try {
            schedule.setParams(objectMapper.writeValueAsString(params));
        } catch (Exception ex) {
            schedule.setParams("{}");
        }
        schedule.setNextRunAt(expression.next(LocalDateTime.now()));
        reportScheduleMapper.insert(schedule);
        return R.ok("Report schedule created", schedule);
    }

    @Operation(summary = "Get report task status")
    @GetMapping("/{taskId}/status")
    public R<?> getTaskStatus(@PathVariable Long taskId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("Task not found");
        }

        Integer roleType = getCurrentRoleType();
        Long userId = requireCurrentUserId();
        if ((roleType == null || roleType != 1) && !userId.equals(task.getCreatedBy())) {
            throw BusinessException.notFound("Task not found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        result.put("startedAt", task.getStartedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("errorMessage", task.getErrorMessage());
        result.put("failureReasonCode", "FAILED".equals(task.getStatus())
                ? ReportFailureReason.detect(task.getErrorMessage()).name()
                : null);
        result.put("resultSummary", task.getResultSummary());
        result.put("updatedAt", task.getCompletedAt() != null ? task.getCompletedAt() : task.getCreatedAt());
        result.put("dataVersion", REPORT_DATA_VERSION);
        return R.ok(result);
    }

    @Operation(summary = "List report schedules")
    @GetMapping("/schedules")
    public R<?> listSchedules() {
        Long userId = requireCurrentUserId();
        Integer roleType = getCurrentRoleType();
        LambdaQueryWrapper<ReportSchedule> wrapper = new LambdaQueryWrapper<>();
        if (roleType == null || roleType != 1) {
            wrapper.eq(ReportSchedule::getCreatedBy, userId);
        }
        wrapper.orderByDesc(ReportSchedule::getCreatedAt);
        return R.ok(reportScheduleMapper.selectList(wrapper));
    }

    @Operation(summary = "Get report schedule detail")
    @GetMapping("/schedules/{id}")
    public R<?> getSchedule(@PathVariable Long id) {
        return R.ok(requireScheduleAccess(id));
    }

    @Log("Toggle report schedule")
    @Operation(summary = "Enable or disable report schedule")
    @PutMapping("/schedules/{id}/toggle")
    public R<?> toggleSchedule(@PathVariable Long id) {
        ReportSchedule schedule = requireScheduleAccess(id);
        schedule.setIsActive(schedule.getIsActive() != null && schedule.getIsActive() == 1 ? 0 : 1);
        if (schedule.getIsActive() == 1) {
            schedule.setNextRunAt(CronExpression.parse(schedule.getCronExpr()).next(LocalDateTime.now()));
        }
        reportScheduleMapper.updateById(schedule);
        return R.ok("Report schedule updated", schedule);
    }

    @Log("Delete report schedule")
    @Operation(summary = "Delete report schedule")
    @DeleteMapping("/schedules/{id}")
    public R<?> deleteSchedule(@PathVariable Long id) {
        ReportSchedule schedule = requireScheduleAccess(id);
        reportScheduleMapper.deleteById(schedule.getId());
        return R.ok("Report schedule deleted");
    }

    @Log("Delete analysis report")
    @Operation(summary = "Delete report")
    @DeleteMapping("/{id}")
    public R<?> deleteReport(@PathVariable Long id) {
        AnalysisReport report = requireReport(id);
        checkReportAccess(report);
        reportMapper.deleteById(id);
        return R.ok("Report deleted successfully");
    }

    @Log("Batch delete analysis reports")
    @Operation(summary = "Batch delete reports")
    @DeleteMapping("/batch")
    public R<?> batchDeleteReports(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.fail("No IDs provided");
        }
        for (Long id : ids) {
            AnalysisReport report = reportMapper.selectById(id);
            if (report == null) {
                continue;
            }
            try {
                checkReportAccess(report);
                reportMapper.deleteById(id);
            } catch (Exception ignored) {
            }
        }
        return R.ok("Reports batch deleted successfully");
    }

    @Operation(summary = "Download report metadata")
    @GetMapping("/{id}/download")
    public R<?> downloadReport(@PathVariable Long id) {
        AnalysisReport report = requireReport(id);
        checkReportAccess(report);
        report.setViewCount((report.getViewCount() == null ? 0 : report.getViewCount()) + 1);
        reportMapper.updateById(report);
        return R.ok(report);
    }

    @SuppressWarnings("unchecked")
    @Operation(summary = "Drill-down report details")
    @GetMapping("/{id}/drill")
    public R<?> drillReport(@PathVariable Long id) {
        AnalysisReport report = requireReport(id);
        checkReportAccess(report);

        Map<String, Object> analysisData = sanitizeAnalysisData(parseAnalysisData(report.getAnalysisData()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", report.getId());
        payload.put("reportName", report.getReportName());
        payload.put("reportType", report.getReportType());
        payload.put("updatedAt", report.getGeneratedAt() != null ? report.getGeneratedAt() : report.getCreatedAt());
        payload.put("dataVersion", REPORT_DATA_VERSION);
        payload.put("summary", report.getDescription());
        payload.put("targetAudience", analysisData.getOrDefault("targetAudience", "报告使用者"));
        payload.put("reportFocus", analysisData.getOrDefault("reportFocus", ""));
        payload.put("templateDescription", analysisData.getOrDefault("templateDescription", ""));
        payload.put("sections", analysisData);
        payload.put("chartCards", analysisData.getOrDefault("chartCards", Collections.emptyList()));
        payload.put("jobSamples", analysisData.getOrDefault("jobSamples", Collections.emptyList()));
        payload.put("chartInsights", analysisData.getOrDefault("chartInsights", Collections.emptyList()));
        payload.put("recommendations", analysisData.getOrDefault("recommendations", Collections.emptyList()));
        payload.put("actionPlan", analysisData.getOrDefault("actionPlan", Collections.emptyList()));
        payload.put("comparisonItems", analysisData.getOrDefault("comparisonItems", Collections.emptyList()));
        payload.put("roleTemplate", analysisData.getOrDefault("roleTemplate", Collections.emptyMap()));
        payload.put("reportMeta", analysisData.getOrDefault("reportMeta", Collections.emptyMap()));
        payload.put("reportGovernance", analysisData.getOrDefault("reportGovernance", Collections.emptyMap()));
        payload.put("reportVersioning", analysisData.getOrDefault("reportVersioning", Collections.emptyMap()));
        payload.put("reportLifecycle", analysisData.getOrDefault("reportLifecycle", buildDefaultLifecycle(report)));
        payload.put("userContext", analysisData.getOrDefault("userContext", Collections.emptyMap()));
        payload.put("advisory", getOptionalCurrentUserId() == null
                ? Collections.emptyMap()
                : sanitizeAnalysisData(userInsightService.buildPlatformAdvisory(getOptionalCurrentUserId())));
        return R.ok(payload);
    }

    public static class ReviewRequest {
        @NotBlank(message = "action is required")
        private String action;
        private String comment;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    @Log("Submit report for review")
    @Operation(summary = "Submit report for governance review")
    @PostMapping("/{id}/submit-review")
    public R<?> submitForReview(@PathVariable Long id) {
        AnalysisReport report = requireReport(id);
        checkReportAccess(report);

        Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
        Map<String, Object> lifecycle = mergeLifecycle(analysisData, report);
        String state = String.valueOf(lifecycle.get("state"));
        if ("APPROVED".equals(state) || "PUBLISHED".equals(state)) {
            return R.ok("Report already approved", buildReportSummary(report));
        }

        lifecycle.put("state", "IN_REVIEW");
        lifecycle.put("stateLabel", "审核中");
        lifecycle.put("submittedBy", requireCurrentUserId());
        lifecycle.put("submittedAt", LocalDateTime.now().toString());
        lifecycle.put("reviewComment", "");
        appendLifecycleEvent(lifecycle, "SUBMITTED", "提交审核", requireCurrentUserId(), "");
        analysisData.put("reportLifecycle", lifecycle);
        saveAnalysisData(report, analysisData);
        return R.ok("Report submitted for review", buildReportSummary(report));
    }

    @Log("Review report")
    @Operation(summary = "Approve or reject report")
    @PostMapping("/{id}/review")
    public R<?> reviewReport(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        requireAdminRole();
        AnalysisReport report = requireReport(id);

        Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
        Map<String, Object> lifecycle = mergeLifecycle(analysisData, report);
        String action = req.getAction() == null ? "" : req.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw BusinessException.of(400, "Review action must be APPROVE or REJECT");
        }

        lifecycle.put("reviewedBy", requireCurrentUserId());
        lifecycle.put("reviewedAt", LocalDateTime.now().toString());
        lifecycle.put("reviewComment", req.getComment() == null ? "" : req.getComment().trim());
        if ("APPROVE".equals(action)) {
            lifecycle.put("state", "APPROVED");
            lifecycle.put("stateLabel", "已审核");
            appendLifecycleEvent(lifecycle, "APPROVED", "审核通过", requireCurrentUserId(), req.getComment());
        } else {
            lifecycle.put("state", "REJECTED");
            lifecycle.put("stateLabel", "已驳回");
            report.setIsPublic(0);
            appendLifecycleEvent(lifecycle, "REJECTED", "审核驳回", requireCurrentUserId(), req.getComment());
        }
        analysisData.put("reportLifecycle", lifecycle);
        saveAnalysisData(report, analysisData);
        return R.ok("Report reviewed", buildReportSummary(report));
    }

    @Log("Publish report")
    @Operation(summary = "Publish approved report")
    @PostMapping("/{id}/publish")
    public R<?> publishReport(@PathVariable Long id) {
        requireAdminRole();
        AnalysisReport report = requireReport(id);

        Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
        Map<String, Object> lifecycle = mergeLifecycle(analysisData, report);
        String state = String.valueOf(lifecycle.get("state"));
        if (!"APPROVED".equals(state) && !"PUBLISHED".equals(state)) {
            throw BusinessException.of(400, "Only approved reports can be published");
        }

        lifecycle.put("state", "PUBLISHED");
        lifecycle.put("stateLabel", "已发布");
        lifecycle.put("publishedBy", requireCurrentUserId());
        lifecycle.put("publishedAt", LocalDateTime.now().toString());
        report.setIsPublic(1);
        appendLifecycleEvent(lifecycle, "PUBLISHED", "公开发布", requireCurrentUserId(), "");
        analysisData.put("reportLifecycle", lifecycle);
        saveAnalysisData(report, analysisData);
        return R.ok("Report published", buildReportSummary(report));
    }

    @Log("Unpublish report")
    @Operation(summary = "Unpublish report")
    @PostMapping("/{id}/unpublish")
    public R<?> unpublishReport(@PathVariable Long id) {
        requireAdminRole();
        AnalysisReport report = requireReport(id);

        Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
        Map<String, Object> lifecycle = mergeLifecycle(analysisData, report);
        lifecycle.put("state", "APPROVED");
        lifecycle.put("stateLabel", "已审核");
        lifecycle.put("unpublishedBy", requireCurrentUserId());
        lifecycle.put("unpublishedAt", LocalDateTime.now().toString());
        report.setIsPublic(0);
        appendLifecycleEvent(lifecycle, "UNPUBLISHED", "撤回公开", requireCurrentUserId(), "");
        analysisData.put("reportLifecycle", lifecycle);
        saveAnalysisData(report, analysisData);
        return R.ok("Report unpublished", buildReportSummary(report));
    }

    @Operation(summary = "List report version lineage")
    @GetMapping("/{id}/versions")
    public R<?> listReportVersions(@PathVariable Long id) {
        AnalysisReport report = requireReport(id);
        checkReportAccess(report);

        List<Map<String, Object>> versions = reportGenerationService.listReportVersions(
                id,
                getOptionalCurrentUserId(),
                getCurrentRoleType()
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", report.getId());
        payload.put("reportType", report.getReportType());
        payload.put("reportName", report.getReportName());
        payload.put("versions", versions);
        payload.put("totalVersions", versions.size());
        return R.ok(payload);
    }

    @Log("Download report PDF")
    @Operation(summary = "Export report as PDF")
    @GetMapping("/{id}/pdf")
    public void downloadPdf(@PathVariable Long id, HttpServletResponse response) {
        exportReport(id, "pdf", response);
    }

    @SuppressWarnings("unchecked")
    @Log("Export report")
    @Operation(summary = "Export report in specified format (pdf/html/md)")
    @GetMapping("/{id}/export")
    public void exportReport(@PathVariable Long id,
                             @RequestParam(defaultValue = "pdf") String format,
                             HttpServletResponse response) {
        AnalysisReport report = requireReport(id);
        checkReportAccess(report);

        try {
            Map<String, Object> analysisData = Collections.emptyMap();
            if (report.getAnalysisData() != null) {
                analysisData = objectMapper.readValue(report.getAnalysisData(), Map.class);
            }
            analysisData = sanitizeAnalysisData(analysisData);

            String filename = URLEncoder.encode(report.getReportName(), StandardCharsets.UTF_8.name());
            byte[] content;

            if ("html".equalsIgnoreCase(format)) {
                String html = pdfExportService.generateHtml(report.getReportName(), report.getReportType(), analysisData, report.getDescription());
                content = html.getBytes(StandardCharsets.UTF_8);
                response.setContentType(MediaType.TEXT_HTML_VALUE);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename + ".html");
            } else if ("md".equalsIgnoreCase(format)) {
                String md = pdfExportService.generateMarkdown(report.getReportName(), report.getReportType(), analysisData, report.getDescription());
                content = md.getBytes(StandardCharsets.UTF_8);
                response.setContentType(MediaType.TEXT_MARKDOWN_VALUE);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename + ".md");
            } else {
                content = pdfExportService.generatePdf(report.getReportName(), report.getReportType(), analysisData, report.getDescription());
                response.setContentType(MediaType.APPLICATION_PDF_VALUE);
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename + ".pdf");
            }

            response.getOutputStream().write(content);
            response.flushBuffer();

            report.setDownloadCount((report.getDownloadCount() == null ? 0 : report.getDownloadCount()) + 1);
            reportMapper.updateById(report);
        } catch (Exception e) {
            throw BusinessException.of(500, "Export generation failed: " + e.getMessage());
        }
    }

    private void collectStudentMissingRequirements(Long userId, List<Map<String, Object>> missingRequirements) {
        Map<String, Object> userContext = userInsightService.loadUserContext(userId);
        @SuppressWarnings("unchecked")
        List<String> skills = userContext.get("skills") instanceof List
                ? (List<String>) userContext.get("skills")
                : Collections.emptyList();
        String profileSummary = String.valueOf(userContext.getOrDefault("profileSummary", "")).trim();
        String targetCity = String.valueOf(userContext.getOrDefault("targetCityCode", "")).trim();

        if (skills.isEmpty() || profileSummary.isEmpty()) {
            missingRequirements.add(requirementItem(
                    "student_profile_import",
                    "请先导入简历并完成画像解析",
                    "在智能推荐页上传简历文件，系统会自动提取技能和目标方向。",
                    "/recommend",
                    "前往 智能推荐 / 资料导入",
                    actionItem("/recommend", "上传简历文件", "上传 PDF/DOCX/MD/TXT 后自动回填画像字段。")
            ));
        }

        String recommendLastRun = redisTemplate.opsForValue().get(STUDENT_RECOMMEND_LAST_RUN_KEY_PREFIX + userId);
        if (!StringUtils.hasText(recommendLastRun) || targetCity.isEmpty()) {
            missingRequirements.add(requirementItem(
                    "student_recommend_analysis",
                    "请先运行岗位匹配分析",
                    "报告需要智能推荐分析结果，当前未检测到有效的推荐分析记录。",
                    "/recommend",
                    "前往 智能推荐 / 职位匹配",
                    actionItem("/recommend", "运行岗位匹配", "填写目标城市和技能后，先执行一次岗位匹配分析。")
            ));
        }
    }

    private void collectTeacherMissingRequirements(Long userId, String major, List<Map<String, Object>> missingRequirements) {
        if (!hasCurriculumData(userId, major)) {
            missingRequirements.add(requirementItem(
                    "teacher_curriculum",
                    "请先上传课程清单 Excel",
                    "教师报告依赖课程结构数据，未检测到可用课程清单。",
                    "/teacher",
                    "前往 课程供需 / 课程清单",
                    actionItem("/teacher", "上传课程清单", "先上传课程清单 Excel，再继续教学报告分析。")
            ));
        }
        if (!hasTeacherMaterial(userId, major, "SYLLABUS")) {
            missingRequirements.add(requirementItem(
                    "teacher_syllabus",
                    "请先上传教学大纲 Excel",
                    "教师报告依赖教学大纲字段，当前未检测到上传记录。",
                    "/teacher",
                    "前往 课程供需 / 教学大纲",
                    actionItem("/teacher", "上传教学大纲", "上传教学大纲 Excel 以解锁教学建议报告。")
            ));
        }
        if (!hasTeacherMaterial(userId, major, "STUDENT_STATUS")) {
            missingRequirements.add(requirementItem(
                    "teacher_student_status",
                    "请先上传学生情况 Excel",
                    "教师报告依赖学生能力与就业状态数据，当前未检测到上传记录。",
                    "/teacher",
                    "前往 课程供需 / 学生情况",
                    actionItem("/teacher", "上传学生情况", "上传学生情况 Excel 后可生成完整教师报告。")
            ));
        }
    }

    private void collectAdminMissingRequirements(List<Map<String, Object>> missingRequirements) {
        long userCount = sysUserMapper.selectCount(null);
        long jobCount = jobPostingMapper.selectCount(null);
        if (userCount <= 0) {
            missingRequirements.add(requirementItem(
                    "admin_user_dashboard",
                    "请先补齐用户看板数据",
                    "管理员报告依赖用户分层与行为数据，当前用户样本为空。",
                    "/admin",
                    "前往 用户看板补数",
                    actionItem("/admin", "补齐用户数据", "先同步用户数据，再生成运营类报告。")
            ));
        }
        if (jobCount <= 0) {
            missingRequirements.add(requirementItem(
                    "admin_operation_dashboard",
                    "请先补齐运营看板数据",
                    "管理员报告依赖岗位与供需样本，当前岗位样本为空。",
                    "/data-collector",
                    "前往 数据采集",
                    actionItem("/data-collector", "采集岗位数据", "先执行岗位采集，再生成运营分析报告。")
            ));
        }
    }

    private boolean hasCurriculumData(Long userId, String major) {
        LambdaQueryWrapper<Curriculum> wrapper = new LambdaQueryWrapper<Curriculum>()
                .eq(Curriculum::getUploadedBy, userId)
                .eq(Curriculum::getIsActive, 1);
        if (StringUtils.hasText(major)) {
            wrapper.like(Curriculum::getMajor, major.trim());
        }
        return curriculumMapper.selectCount(wrapper) > 0;
    }

    private Map<String, Object> requirementItem(String key, String title, String detail,
                                                String routePath, String actionLabel,
                                                Map<String, Object> action) {
        Map<String, Object> item = new HashMap<>();
        item.put("key", key);
        item.put("title", title);
        item.put("detail", detail);
        item.put("routePath", routePath);
        item.put("actionLabel", actionLabel);
        item.put("action", action);
        return item;
    }

    private Map<String, Object> actionItem(String path, String label, String detail) {
        Map<String, Object> action = new HashMap<>();
        action.put("path", path);
        action.put("label", label);
        action.put("detail", detail);
        return action;
    }

    private Map<String, Object> firstAction(List<Map<String, Object>> missingRequirements) {
        if (missingRequirements.isEmpty()) {
            return actionItem("/report-center", "生成角色专属报告", "前置数据已准备完成，可直接生成报告。");
        }
        Object action = missingRequirements.get(0).get("action");
        if (action instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) action;
            return typed;
        }
        return actionItem("/report-center", "检查前置数据", "请先完成数据准备。");
    }

    private Map<String, Object> buildAssistantReadiness(List<Map<String, Object>> missingRequirements,
                                                        Map<String, Object> primaryAction) {
        Map<String, Object> assistant = new HashMap<>();
        boolean ready = missingRequirements.isEmpty();
        assistant.put("ready", ready);
        if (ready) {
            assistant.put("message", "前置数据已就绪，可直接在 AI 助手中发起报告分析与解读。");
        } else {
            String label = String.valueOf(primaryAction.getOrDefault("label", "先完成前置步骤"));
            String path = String.valueOf(primaryAction.getOrDefault("path", "/report-center"));
            assistant.put("message", "当前资料不足，请先完成“" + label + "”，再使用 AI 助手深度分析。");
            assistant.put("nextPath", path);
        }
        return assistant;
    }

    private String roleLabel(Integer roleType) {
        if (roleType != null && roleType == SysUser.ROLE_ADMIN) {
            return "管理员";
        }
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            return "教师";
        }
        return "学生";
    }

    private void validateReportType(Integer roleType, String reportType) {
        if (!reportGenerationService.isReportTypeAllowed(roleType, reportType)) {
            throw BusinessException.of(400, "Current role cannot generate this report type");
        }
    }

    private void validateTeacherReportPrerequisites(Long userId, Integer roleType, String reportType, Map<String, Object> params) {
        if (roleType == null || roleType != SysUser.ROLE_TEACHER) {
            return;
        }
        if (!requiresTeacherPreparation(reportType)) {
            return;
        }

        String major = params == null ? "" : String.valueOf(params.getOrDefault("major", "")).trim();
        List<String> missing = new ArrayList<>();

        LambdaQueryWrapper<Curriculum> curriculumWrapper = new LambdaQueryWrapper<Curriculum>()
                .eq(Curriculum::getUploadedBy, userId)
                .eq(Curriculum::getIsActive, 1);
        if (StringUtils.hasText(major)) {
            curriculumWrapper.like(Curriculum::getMajor, major);
        }
        if (curriculumMapper.selectCount(curriculumWrapper) == 0) {
            missing.add("课程 Excel");
        }
        if (!hasTeacherMaterial(userId, major, "SYLLABUS")) {
            missing.add("教学大纲 Excel");
        }
        if (!hasTeacherMaterial(userId, major, "STUDENT_STATUS")) {
            missing.add("学生情况 Excel");
        }

        if (!missing.isEmpty()) {
            throw BusinessException.of(400, "生成教学综合报告前，请先上传：" + String.join("、", missing));
        }
    }

    private boolean requiresTeacherPreparation(String reportType) {
        String normalized = normalizeType(reportType);
        return ReportGenerationService.REPORT_COMPREHENSIVE.equals(normalized)
                || ReportGenerationService.REPORT_TEACHING_ADVICE.equals(normalized);
    }

    private boolean hasTeacherMaterial(Long userId, String major, String materialType) {
        LambdaQueryWrapper<TeacherMaterialAsset> wrapper = new LambdaQueryWrapper<TeacherMaterialAsset>()
                .eq(TeacherMaterialAsset::getUserId, userId)
                .eq(TeacherMaterialAsset::getMaterialType, materialType);
        if (StringUtils.hasText(major)) {
            wrapper.and(w -> w.eq(TeacherMaterialAsset::getMajor, major)
                    .or().isNull(TeacherMaterialAsset::getMajor)
                    .or().eq(TeacherMaterialAsset::getMajor, ""));
        }
        return teacherMaterialAssetMapper.selectCount(wrapper) > 0;
    }

    private String normalizeReportName(String reportName, String reportType, Integer roleType) {
        String cleaned = reportName == null ? "" : reportName.trim();
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return reportGenerationService.defaultReportName(roleType, reportType);
    }

    private String normalizeType(String reportType) {
        return reportType == null ? ReportGenerationService.REPORT_COMPREHENSIVE : reportType.trim().toUpperCase();
    }

    private AnalysisReport requireReport(Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("Report not found");
        }
        return report;
    }

    private Long requireCurrentUserId() {
        Long userId = getOptionalCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("登录状态已失效，请重新登录");
        }
        return userId;
    }

    private Long getOptionalCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getPrincipal() instanceof Long ? (Long) auth.getPrincipal() : null;
    }

    private Integer getCurrentRoleType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Integer) {
            return (Integer) auth.getCredentials();
        }
        return 0;
    }

    private void requireAdminRole() {
        if (getCurrentRoleType() == null || getCurrentRoleType() != 1) {
            throw BusinessException.forbidden("Only admin can review or publish reports");
        }
    }

    private ReportSchedule requireScheduleAccess(Long id) {
        ReportSchedule schedule = reportScheduleMapper.selectById(id);
        if (schedule == null) {
            throw BusinessException.notFound("Report schedule not found");
        }
        Integer roleType = getCurrentRoleType();
        Long userId = requireCurrentUserId();
        if ((roleType == null || roleType != 1) && !userId.equals(schedule.getCreatedBy())) {
            throw BusinessException.notFound("Report schedule not found");
        }
        return schedule;
    }

    private void checkReportAccess(AnalysisReport report) {
        if (report.getIsPublic() != null && report.getIsPublic() == 1) {
            return;
        }
        Integer roleType = getCurrentRoleType();
        Long userId = requireCurrentUserId();
        if ((roleType == null || roleType != 1) && !userId.equals(report.getGeneratedBy())) {
            throw BusinessException.notFound("Report not found");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAnalysisData(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> sanitizeAnalysisData(Map<String, Object> source) {
        return sensitiveDataMaskingService.maskReportData(source);
    }

    private void saveAnalysisData(AnalysisReport report, Map<String, Object> analysisData) {
        try {
            report.setAnalysisData(objectMapper.writeValueAsString(analysisData));
        } catch (Exception ex) {
            throw BusinessException.of(500, "Failed to persist report lifecycle");
        }
        reportMapper.updateById(report);
    }

    private List<Map<String, Object>> buildReportSummaries(List<AnalysisReport> reports) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AnalysisReport report : reports) {
            items.add(buildReportSummary(report));
        }
        return items;
    }

    private Map<String, Object> buildReportSummary(AnalysisReport report) {
        Map<String, Object> analysisData = parseAnalysisData(report.getAnalysisData());
        Map<String, Object> lifecycle = mergeLifecycle(analysisData, report);
        Map<String, Object> governance = asMap(analysisData.get("reportGovernance"));
        Map<String, Object> versioning = asMap(analysisData.get("reportVersioning"));

        Map<String, Object> item = new HashMap<>();
        item.put("id", report.getId());
        item.put("taskId", report.getTaskId());
        item.put("reportName", report.getReportName());
        item.put("reportType", report.getReportType());
        item.put("reportFormat", report.getReportFormat());
        item.put("description", report.getDescription());
        item.put("isPublic", report.getIsPublic());
        item.put("viewCount", report.getViewCount());
        item.put("downloadCount", report.getDownloadCount());
        item.put("generatedBy", report.getGeneratedBy());
        item.put("generatedAt", report.getGeneratedAt());
        item.put("createdAt", report.getCreatedAt());
        item.put("updatedAt", report.getGeneratedAt() != null ? report.getGeneratedAt() : report.getCreatedAt());
        item.put("dataVersion", REPORT_DATA_VERSION);
        item.put("reportLifecycle", lifecycle);
        item.put("reportGovernance", governance);
        item.put("reportVersioning", versioning);
        return item;
    }

    private Map<String, Object> mergeLifecycle(Map<String, Object> analysisData, AnalysisReport report) {
        Map<String, Object> lifecycle = new HashMap<>(buildDefaultLifecycle(report));
        lifecycle.putAll(asMap(analysisData.get("reportLifecycle")));
        lifecycle.put("history", normalizeLifecycleHistory(lifecycle.get("history")));
        if ((report.getIsPublic() != null && report.getIsPublic() == 1) && !"PUBLISHED".equals(String.valueOf(lifecycle.get("state")))) {
            lifecycle.put("state", "PUBLISHED");
            lifecycle.put("stateLabel", "已发布");
        }
        return lifecycle;
    }

    private Map<String, Object> buildDefaultLifecycle(AnalysisReport report) {
        Map<String, Object> lifecycle = new HashMap<>();
        lifecycle.put("state", report.getIsPublic() != null && report.getIsPublic() == 1 ? "PUBLISHED" : "DRAFT");
        lifecycle.put("stateLabel", report.getIsPublic() != null && report.getIsPublic() == 1 ? "已发布" : "草稿");
        lifecycle.put("submittedBy", null);
        lifecycle.put("submittedAt", null);
        lifecycle.put("reviewedBy", null);
        lifecycle.put("reviewedAt", null);
        lifecycle.put("reviewComment", "");
        lifecycle.put("publishedBy", null);
        lifecycle.put("publishedAt", null);
        lifecycle.put("history", new ArrayList<>());
        return lifecycle;
    }

    private void appendLifecycleEvent(Map<String, Object> lifecycle, String code, String label, Long operatorId, String comment) {
        List<Map<String, Object>> history = normalizeLifecycleHistory(lifecycle.get("history"));
        Map<String, Object> item = new HashMap<>();
        item.put("code", code);
        item.put("label", label);
        item.put("operatorId", operatorId);
        item.put("comment", comment == null ? "" : comment.trim());
        item.put("occurredAt", LocalDateTime.now().toString());
        history.add(0, item);
        lifecycle.put("history", history);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeLifecycleHistory(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                result.add(new HashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }
}
