package com.career.platform.crawl.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.crawl.entity.CrawlLog;
import com.career.platform.crawl.entity.CrawlTask;
import com.career.platform.crawl.mapper.CrawlLogMapper;
import com.career.platform.crawl.mapper.CrawlTaskMapper;
import com.career.platform.crawl.service.DataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Crawl Task Management", description = "Manage Python crawler tasks, logs, and quality governance")
@RestController
@RequestMapping("/api/v1/crawl/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CrawlTaskController {

    private final CrawlTaskMapper taskMapper;
    private final CrawlLogMapper logMapper;
    private final DataQualityService dataQualityService;

    @Operation(summary = "Crawler task list")
    @GetMapping
    public R<?> listTasks(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        LambdaQueryWrapper<CrawlTask> wrapper = new LambdaQueryWrapper<>();
        if (channel != null && !channel.isEmpty()) {
            wrapper.eq(CrawlTask::getChannel, channel);
        }
        if (status != null) {
            wrapper.eq(CrawlTask::getStatus, status);
        }
        wrapper.orderByDesc(CrawlTask::getCreateTime);

        IPage<CrawlTask> result = taskMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Data
    public static class CreateTaskRequest {
        @NotBlank(message = "taskName is required")
        private String taskName;
        @NotBlank(message = "channel is required")
        private String channel;
        private String keywords;
        private String city;
        private Integer priority;
    }

    @Log("Create crawl task")
    @Operation(summary = "Create crawler task")
    @PostMapping
    public R<?> createTask(@Valid @RequestBody CreateTaskRequest req) {
        CrawlTask task = new CrawlTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setTaskName(req.getTaskName());
        task.setChannel(req.getChannel());
        task.setKeywords(req.getKeywords());
        task.setCity(req.getCity());
        task.setStatus(0);
        task.setPriority(req.getPriority() == null ? 5 : req.getPriority());
        task.setTotalCount(0);
        task.setFinishedCount(0);
        task.setDuplicateCount(0);
        task.setCreateUser(String.valueOf(getCurrentUserId()));
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("executionMode", "managed-python-crawler");
        return R.ok("Crawler task created", data);
    }

    @Operation(summary = "Crawler task detail")
    @GetMapping("/{id}")
    public R<?> getTask(@PathVariable String id) {
        CrawlTask task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.notFound("Task not found");
        }
        return R.ok(task);
    }

    @Data
    public static class UpdateTaskStatusRequest {
        private Integer status;
    }

    @Log("Update crawl task status")
    @Operation(summary = "Pause resume cancel task")
    @PutMapping("/{id}/status")
    public R<?> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateTaskStatusRequest req) {
        CrawlTask task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.notFound("Task not found");
        }

        Integer newStatus = req.getStatus();
        if (newStatus == null || newStatus < 0 || newStatus > 3) {
            throw BusinessException.of(400, "Unsupported status: 0 / 1 / 2 / 3");
        }

        task.setStatus(newStatus);
        if (newStatus == 1 && task.getStartTime() == null) {
            task.setStartTime(LocalDateTime.now());
        }
        if (newStatus == 2 || newStatus == 3) {
            task.setEndTime(LocalDateTime.now());
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("status", task.getStatus());
        return R.ok("Task status updated", data);
    }

    @Operation(summary = "Crawler task logs")
    @GetMapping("/{taskId}/logs")
    public R<?> taskLogs(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        IPage<CrawlLog> result = logMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<CrawlLog>()
                        .eq(CrawlLog::getTaskId, taskId)
                        .orderByDesc(CrawlLog::getCreateTime)
        );
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Operation(summary = "Data quality report")
    @GetMapping("/quality")
    public R<?> dataQuality() {
        return R.ok(dataQualityService.getQualityReport());
    }

    @Log("Backfill crawl history snapshots")
    @Operation(summary = "Backfill job history snapshots")
    @PostMapping("/quality/history/backfill")
    public R<?> backfillHistory(@RequestParam(defaultValue = "100") int limit) {
        int inserted = dataQualityService.backfillJobHistorySnapshots(limit);
        Map<String, Object> payload = new HashMap<>();
        payload.put("inserted", inserted);
        return R.ok(payload);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
