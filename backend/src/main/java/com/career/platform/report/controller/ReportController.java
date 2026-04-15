package com.career.platform.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.entity.ReportSchedule;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.mapper.ReportScheduleMapper;
import com.career.platform.report.service.PdfExportService;
import com.career.platform.report.service.ReportGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Analysis Reports", description = "Report generation, task status, listing, and PDF export")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final AnalysisReportMapper reportMapper;
    private final AnalysisTaskMapper taskMapper;
    private final ReportScheduleMapper reportScheduleMapper;
    private final ObjectMapper objectMapper;
    private final ReportGenerationService reportGenerationService;
    private final PdfExportService pdfExportService;
    private final UserInsightService userInsightService;

    @Operation(summary = "Get report list")
    @GetMapping
    public R<?> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Long userId = getCurrentUserId();
        Integer roleType = getCurrentRoleType();

        LambdaQueryWrapper<AnalysisReport> wrapper = new LambdaQueryWrapper<>();
        if (roleType == null || roleType != 1) {
            wrapper.eq(AnalysisReport::getGeneratedBy, userId);
        }
        wrapper.orderByDesc(AnalysisReport::getGeneratedAt);

        IPage<AnalysisReport> result = reportMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Operation(summary = "Get public report list")
    @GetMapping("/public")
    public R<?> publicReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        LambdaQueryWrapper<AnalysisReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisReport::getIsPublic, 1)
                .orderByDesc(AnalysisReport::getGeneratedAt);

        IPage<AnalysisReport> result = reportMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Data
    public static class GenerateRequest {
        @NotBlank(message = "reportName is required")
        private String reportName;

        @NotBlank(message = "reportType is required")
        private String reportType;

        private Map<String, Object> params = Collections.emptyMap();
    }

    @Data
    public static class ScheduleRequest {
        @NotBlank(message = "scheduleName is required")
        private String scheduleName;

        @NotBlank(message = "reportType is required")
        private String reportType;

        @NotBlank(message = "cronExpr is required")
        private String cronExpr;

        private Map<String, Object> params = Collections.emptyMap();
    }

    @Log("Generate analysis report")
    @Operation(summary = "Create report generation task")
    @PostMapping("/generate")
    public R<?> generateReport(@Valid @RequestBody GenerateRequest req) {
        Long userId = getCurrentUserId();
        Integer roleType = getCurrentRoleType();
        Map<String, Object> params = req.getParams() == null ? new HashMap<>() : new HashMap<>(req.getParams());
        if (roleType == null || roleType != 1) {
            params.put("targetRoleType", roleType == null ? 0 : roleType);
        }

        AnalysisTask task = new AnalysisTask();
        task.setTaskName(req.getReportName());
        task.setTaskType(req.getReportType().toUpperCase());
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
        reportGenerationService.executeReportGeneration(task.getId(), req.getReportType(), req.getReportName(), userId);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        return R.ok("Report generation task created", data);
    }

    @Log("Create report schedule")
    @Operation(summary = "Create scheduled report plan")
    @PostMapping("/schedule")
    public R<?> createSchedule(@Valid @RequestBody ScheduleRequest req) {
        if (reportScheduleMapper == null) {
            throw BusinessException.of(500, "Report schedule service not available");
        }

        CronExpression expression = CronExpression.parse(req.getCronExpr());
        ReportSchedule schedule = new ReportSchedule();
        schedule.setScheduleName(req.getScheduleName());
        schedule.setReportType(req.getReportType().toUpperCase());
        schedule.setCronExpr(req.getCronExpr());
        schedule.setIsActive(1);
        schedule.setCreatedBy(getCurrentUserId());
        schedule.setCreatedAt(LocalDateTime.now());
        try {
            schedule.setParams(objectMapper.writeValueAsString(req.getParams()));
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
        Long userId = getCurrentUserId();
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
        result.put("resultSummary", task.getResultSummary());
        return R.ok(result);
    }

    @Operation(summary = "List report schedules")
    @GetMapping("/schedules")
    public R<?> listSchedules() {
        if (reportScheduleMapper == null) {
            throw BusinessException.of(500, "Report schedule service not available");
        }

        Long userId = getCurrentUserId();
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
        ReportSchedule schedule = requireScheduleAccess(id);
        return R.ok(schedule);
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

    @Operation(summary = "Download report metadata")
    @GetMapping("/{id}/download")
    public R<?> downloadReport(@PathVariable Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("Report not found");
        }
        checkReportAccess(report);

        report.setViewCount((report.getViewCount() == null ? 0 : report.getViewCount()) + 1);
        reportMapper.updateById(report);
        return R.ok(report);
    }

    @SuppressWarnings("unchecked")
    @Operation(summary = "Drill-down report details")
    @GetMapping("/{id}/drill")
    public R<?> drillReport(@PathVariable Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("Report not found");
        }
        checkReportAccess(report);

        Map<String, Object> analysisData = Collections.emptyMap();
        try {
            if (report.getAnalysisData() != null) {
                analysisData = objectMapper.readValue(report.getAnalysisData(), Map.class);
            }
        } catch (Exception ignored) {
            analysisData = Collections.emptyMap();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", report.getId());
        payload.put("reportName", report.getReportName());
        payload.put("reportType", report.getReportType());
        payload.put("summary", report.getDescription());
        payload.put("targetAudience", analysisData.getOrDefault("targetAudience", "Personal career planning user"));
        payload.put("reportFocus", analysisData.getOrDefault("reportFocus", ""));
        payload.put("sections", analysisData);
        payload.put("chartCards", analysisData.getOrDefault("chartCards", Collections.emptyList()));
        payload.put("sampleJobs", analysisData.getOrDefault("hotJobs", Collections.emptyList()));
        payload.put("jobSamples", analysisData.getOrDefault("jobSamples", Collections.emptyList()));
        payload.put("chartInsights", analysisData.getOrDefault("chartInsights", Collections.emptyList()));
        payload.put("recommendations", analysisData.getOrDefault("recommendations", Collections.emptyList()));
        payload.put("actionPlan", analysisData.getOrDefault("actionPlan", Collections.emptyList()));
        payload.put("comparisonItems", analysisData.getOrDefault("comparisonItems", Collections.emptyList()));
        payload.put("roleTemplate", analysisData.getOrDefault("roleTemplate", Collections.emptyMap()));
        payload.put("userContext", analysisData.getOrDefault("userContext", Collections.emptyMap()));
        payload.put("advisory", userInsightService.buildPlatformAdvisory(getCurrentUserId()));
        return R.ok(payload);
    }

    @SuppressWarnings("unchecked")
    @Log("Download report PDF")
    @Operation(summary = "Export report as PDF")
    @GetMapping("/{id}/pdf")
    public void downloadPdf(@PathVariable Long id, HttpServletResponse response) {
        if (pdfExportService == null) {
            throw BusinessException.of(500, "PDF service not available");
        }

        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("Report not found");
        }
        checkReportAccess(report);

        try {
            Map<String, Object> analysisData = Collections.emptyMap();
            if (report.getAnalysisData() != null) {
                analysisData = objectMapper.readValue(report.getAnalysisData(), Map.class);
            }

            byte[] pdf = pdfExportService.generatePdf(
                    report.getReportName(),
                    report.getReportType(),
                    analysisData,
                    report.getDescription()
            );

            String filename = URLEncoder.encode(report.getReportName() + ".pdf", StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename);
            response.getOutputStream().write(pdf);
            response.flushBuffer();

            report.setDownloadCount((report.getDownloadCount() == null ? 0 : report.getDownloadCount()) + 1);
            reportMapper.updateById(report);
        } catch (Exception e) {
            throw BusinessException.of(500, "PDF generation failed: " + e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Please login first");
        }
        if (auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        throw BusinessException.unauthorized("Invalid login state");
    }

    private Integer getCurrentRoleType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() != null) {
            return (Integer) auth.getCredentials();
        }
        return 0;
    }

    private ReportSchedule requireScheduleAccess(Long id) {
        if (reportScheduleMapper == null) {
            throw BusinessException.of(500, "Report schedule service not available");
        }
        ReportSchedule schedule = reportScheduleMapper.selectById(id);
        if (schedule == null) {
            throw BusinessException.notFound("Report schedule not found");
        }
        Integer roleType = getCurrentRoleType();
        Long userId = getCurrentUserId();
        if ((roleType == null || roleType != 1) && !userId.equals(schedule.getCreatedBy())) {
            throw BusinessException.notFound("Report schedule not found");
        }
        return schedule;
    }

    private void checkReportAccess(AnalysisReport report) {
        Integer roleType = getCurrentRoleType();
        Long userId = getCurrentUserId();
        if ((roleType == null || roleType != 1) && !userId.equals(report.getGeneratedBy())) {
            throw BusinessException.notFound("Report not found");
        }
    }
}
