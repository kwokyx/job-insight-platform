package com.career.platform.report.controller;

import com.career.platform.common.annotation.Log;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.service.ReportGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "分析报告", description = "报告列表、生成与下载")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final AnalysisReportMapper reportMapper;
    private final AnalysisTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final ReportGenerationService reportGenerationService;

    @Operation(summary = "获取报告列表（分页）")
    @GetMapping
    public R<?> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Long userId = getCurrentUserId();
        Integer roleType = getCurrentRoleType();

        LambdaQueryWrapper<AnalysisReport> wrapper = new LambdaQueryWrapper<>();
        if (roleType == null || roleType != 1) {
            wrapper.and(w -> w
                    .eq(AnalysisReport::getGeneratedBy, userId)
                    .or()
                    .eq(AnalysisReport::getIsPublic, 1)
            );
        }
        wrapper.orderByDesc(AnalysisReport::getGeneratedAt);

        IPage<AnalysisReport> result = reportMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Operation(summary = "获取公开报告列表")
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
        @NotBlank(message = "报告名称不能为空")
        private String reportName;

        @NotBlank(message = "报告类型不能为空")
        private String reportType;

        private Map<String, Object> params = Collections.emptyMap();
    }

    @Log("生成分析报告")
    @Operation(summary = "创建报告生成任务")
    @PostMapping("/generate")
    public R<?> generateReport(@Valid @RequestBody GenerateRequest req) {
        Long userId = getCurrentUserId();

        AnalysisTask task = new AnalysisTask();
        task.setTaskName(req.getReportName());
        task.setTaskType(req.getReportType());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreatedBy(userId);
        task.setCreatedAt(LocalDateTime.now());

        try {
            task.setParams(objectMapper.writeValueAsString(req.getParams()));
        } catch (Exception ignored) {
            task.setParams("{}");
        }

        taskMapper.insert(task);
        reportGenerationService.executeReportGeneration(task.getId(), req.getReportType(), req.getReportName(), userId);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        return R.ok("报告生成任务已创建", data);
    }

    @Operation(summary = "查询报告生成进度")
    @GetMapping("/{taskId}/status")
    public R<?> getTaskStatus(@PathVariable Long taskId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("任务不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        result.put("startedAt", task.getStartedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("errorMessage", task.getErrorMessage());
        return R.ok(result);
    }

    @Operation(summary = "获取报告内容")
    @GetMapping("/{id}/download")
    public R<?> downloadReport(@PathVariable Long id) {
        AnalysisReport report = reportMapper.selectById(id);
        if (report == null) {
            throw BusinessException.notFound("报告不存在");
        }

        report.setViewCount(report.getViewCount() + 1);
        reportMapper.updateById(report);
        return R.ok(report);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return (Long) auth.getPrincipal();
    }

    private Integer getCurrentRoleType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() != null) {
            return (Integer) auth.getCredentials();
        }
        return 0;
    }
}
