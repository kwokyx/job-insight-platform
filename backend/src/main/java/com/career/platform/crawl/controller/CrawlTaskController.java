package com.career.platform.crawl.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.crawl.service.CrawlSchedulerGateway;
import com.career.platform.crawl.service.DataQualityService;
import com.career.platform.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Crawl Task Management", description = "Manage crawler tasks with scheduler-center")
@RestController
@RequestMapping("/api/v1/crawl/tasks")
@PreAuthorize("hasRole('ADMIN')")
public class CrawlTaskController {

    private final CrawlSchedulerGateway crawlSchedulerGateway;
    private final DataQualityService dataQualityService;
    private final WarehouseService warehouseService;

    public CrawlTaskController(CrawlSchedulerGateway crawlSchedulerGateway,
                               DataQualityService dataQualityService,
                               WarehouseService warehouseService) {
        this.crawlSchedulerGateway = crawlSchedulerGateway;
        this.dataQualityService = dataQualityService;
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "Crawler task list")
    @GetMapping
    public R<?> listTasks(@RequestParam(required = false) String channel,
                          @RequestParam(required = false) Integer status,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        Map<String, Object> response = crawlSchedulerGateway.listTasks(mapOf(
                "channel", channel,
                "status", status,
                "page", page,
                "size", pageSize
        ));
        Map<String, Object> data = requireData(response);
        List<Map<String, Object>> items = castList(data.get("items"));
        return R.page(normalizeTaskList(items), asLong(data.get("total")), page, pageSize);
    }

    public static class CreateTaskRequest {
        @NotBlank(message = "taskName is required")
        private String taskName;
        @NotBlank(message = "channel is required")
        private String channel;
        private String keywords;
        private String city;
        private Integer priority;
        private Integer pageCount;
        private String scheduleMode;
        private String schedulePreset;
        private String scheduleTime;
        private List<Integer> scheduleDays;
        private Boolean incremental;
        private Integer incrementalPageLimit;
        private Integer stalePageThreshold;
        private Integer lookbackHours;

        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getKeywords() { return keywords; }
        public void setKeywords(String keywords) { this.keywords = keywords; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public Integer getPageCount() { return pageCount; }
        public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
        public String getScheduleMode() { return scheduleMode; }
        public void setScheduleMode(String scheduleMode) { this.scheduleMode = scheduleMode; }
        public String getSchedulePreset() { return schedulePreset; }
        public void setSchedulePreset(String schedulePreset) { this.schedulePreset = schedulePreset; }
        public String getScheduleTime() { return scheduleTime; }
        public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }
        public List<Integer> getScheduleDays() { return scheduleDays; }
        public void setScheduleDays(List<Integer> scheduleDays) { this.scheduleDays = scheduleDays; }
        public Boolean getIncremental() { return incremental; }
        public void setIncremental(Boolean incremental) { this.incremental = incremental; }
        public Integer getIncrementalPageLimit() { return incrementalPageLimit; }
        public void setIncrementalPageLimit(Integer incrementalPageLimit) { this.incrementalPageLimit = incrementalPageLimit; }
        public Integer getStalePageThreshold() { return stalePageThreshold; }
        public void setStalePageThreshold(Integer stalePageThreshold) { this.stalePageThreshold = stalePageThreshold; }
        public Integer getLookbackHours() { return lookbackHours; }
        public void setLookbackHours(Integer lookbackHours) { this.lookbackHours = lookbackHours; }
    }

    @Log("Create crawl task")
    @Operation(summary = "Create crawler task")
    @PostMapping
    public R<?> createTask(@Valid @RequestBody CreateTaskRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_name", req.getTaskName().trim());
        payload.put("channel", req.getChannel());
        payload.put("keywords", toSingleItemList(req.getKeywords()));
        payload.put("city", toSingleItemList(req.getCity()));
        payload.put("priority", req.getPriority() == null ? 5 : req.getPriority());
        payload.put("page_count", req.getPageCount() == null ? 3 : req.getPageCount());
        payload.put("schedule_mode", blankToNull(req.getScheduleMode()));
        payload.put("schedule_preset", blankToNull(req.getSchedulePreset()));
        payload.put("schedule_time", blankToNull(req.getScheduleTime()));
        payload.put("schedule_days", req.getScheduleDays());
        payload.put("incremental", req.getIncremental() != null && req.getIncremental());
        payload.put("incremental_page_limit", req.getIncrementalPageLimit() == null ? 2 : req.getIncrementalPageLimit());
        payload.put("stale_page_threshold", req.getStalePageThreshold() == null ? 1 : req.getStalePageThreshold());
        payload.put("lookback_hours", req.getLookbackHours() == null ? 72 : req.getLookbackHours());
        payload.put("create_user", String.valueOf(getCurrentUserPrincipal()));

        Map<String, Object> response = crawlSchedulerGateway.createTask(payload);
        Map<String, Object> data = requireData(response);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", stringValue(data.get("task_id")));
        result.put("status", data.get("status"));
        result.put("scheduleType", stringValue(data.get("schedule_type")));
        result.put("executionMode", "scheduler-center");
        return R.ok("Crawler task created", result);
    }

    @Operation(summary = "Crawler task detail")
    @GetMapping("/{id}")
    public R<?> getTask(@PathVariable String id) {
        Map<String, Object> response = crawlSchedulerGateway.getTask(id);
        return R.ok(normalizeTask(requireData(response)));
    }

    public static class UpdateTaskStatusRequest {
        private Integer status;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    @Log("Update crawl task status")
    @Operation(summary = "Start pause or sync task")
    @PutMapping("/{id}/status")
    public R<?> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateTaskStatusRequest req) {
        Integer newStatus = req.getStatus();
        if (newStatus == null) {
            throw BusinessException.of(400, "status is required");
        }

        if (newStatus == 1) {
            crawlSchedulerGateway.startTask(id);
            return R.ok("Task started", mapOf("taskId", id, "status", 1));
        }

        if (newStatus == 3 || newStatus == 0) {
            crawlSchedulerGateway.pauseTask(id);
            return R.ok("Task paused", mapOf("taskId", id, "status", 0));
        }

        if (newStatus == 2) {
            return R.ok("Task data synced", runTaskDataSync("MANUAL_TASK_SYNC"));
        }

        throw BusinessException.of(400, "Unsupported status");
    }

    @Operation(summary = "Crawler task logs")
    @GetMapping("/{taskId}/logs")
    public R<?> taskLogs(@PathVariable String taskId,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "50") int pageSize) {
        Map<String, Object> response = crawlSchedulerGateway.taskLogs(mapOf(
                "task_id", taskId,
                "page", page,
                "size", pageSize
        ));
        Map<String, Object> data = requireData(response);
        List<Map<String, Object>> items = castList(data.get("items"));
        return R.page(normalizeLogs(items), asLong(data.get("total")), page, pageSize);
    }

    @Log("Sync crawl data into business tables")
    @Operation(summary = "Sync crawl data into business tables and refresh snapshots")
    @PostMapping("/sync")
    public R<?> syncTaskData() {
        return R.ok(runTaskDataSync("MANUAL_TASK_SYNC"));
    }

    @Operation(summary = "Data quality report")
    @GetMapping("/quality")
    public R<?> dataQuality() {
        Map<String, Object> syncState = warehouseService.crawlBusinessCounts();
        Map<String, Object> result = new LinkedHashMap<>(dataQualityService.getQualityReport());
        result.put("syncState", syncState);
        return R.ok(result);
    }

    @Log("Backfill crawl history snapshots")
    @Operation(summary = "Backfill job history snapshots")
    @PostMapping("/quality/history/backfill")
    public R<?> backfillHistory(@RequestParam(defaultValue = "100") int limit) {
        int inserted = dataQualityService.backfillJobHistorySnapshots(limit);
        return R.ok(mapOf("inserted", inserted));
    }

    private Map<String, Object> runTaskDataSync(String trigger) {
        Map<String, Object> syncResult = warehouseService.syncCrawlToBusinessIncremental();
        Map<String, Object> etlResult = new LinkedHashMap<>();
        try {
            warehouseService.runIncrementalEtl();
            etlResult.put("status", "SUCCESS");
        } catch (Exception e) {
            etlResult.put("status", "DEGRADED");
            etlResult.put("message", e.getMessage());
        }
        Map<String, Object> snapshotResult = warehouseService.refreshPageSnapshots(null, trigger);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sync", syncResult);
        result.put("etl", etlResult);
        result.put("snapshots", snapshotResult);
        return result;
    }

    private Object getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getPrincipal();
    }

    private Map<String, Object> requireData(Map<String, Object> response) {
        if (response == null) {
            throw BusinessException.of(502, "调度中心返回为空");
        }
        Object code = response.get("code");
        if (code instanceof Number && ((Number) code).intValue() != 200) {
            throw BusinessException.of(502, stringValue(response.get("message")));
        }
        Object data = response.get("data");
        if (!(data instanceof Map)) {
            throw BusinessException.of(502, "调度中心响应格式不正确");
        }
        return castMap(data);
    }

    private List<Map<String, Object>> normalizeTaskList(List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            result.add(normalizeTask(item));
        }
        return result;
    }

    private Map<String, Object> normalizeTask(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("taskId", stringValue(source.get("task_id")));
        target.put("parentTaskId", stringValue(source.get("parent_task_id")));
        target.put("taskName", repairText(stringValue(source.get("task_name"))));
        target.put("channel", stringValue(source.get("channel")));
        target.put("keywords", normalizeStringList(source.get("keywords")));
        target.put("city", normalizeStringList(source.get("city")));
        target.put("pageCount", source.get("page_count"));
        target.put("scheduleType", stringValue(source.get("schedule_type")));
        target.put("schedulePreset", stringValue(source.get("schedule_preset")));
        target.put("scheduleTime", stringValue(source.get("schedule_time")));
        target.put("scheduleDays", source.get("schedule_days"));
        target.put("incremental", source.get("incremental"));
        target.put("baselineTaskId", stringValue(source.get("baseline_task_id")));
        target.put("incrementalPageLimit", source.get("incremental_page_limit"));
        target.put("stalePageThreshold", source.get("stale_page_threshold"));
        target.put("lookbackHours", source.get("lookback_hours"));
        target.put("status", normalizeStatus(source.get("status"), source));
        target.put("priority", source.get("priority"));
        target.put("totalCount", source.get("total_count"));
        target.put("finishedCount", source.get("finished_count"));
        target.put("newCount", source.get("new_count"));
        target.put("updatedCount", source.get("updated_count"));
        target.put("duplicateCount", source.get("duplicate_count"));
        target.put("startTime", source.get("start_time"));
        target.put("endTime", source.get("end_time"));
        target.put("lastSuccessAt", source.get("last_success_at"));
        target.put("watermarkPublishDate", source.get("watermark_publish_date"));
        target.put("watermarkCrawlTime", source.get("watermark_crawl_time"));
        target.put("createUser", stringValue(source.get("create_user")));
        target.put("createTime", firstNonNull(source.get("created_at"), source.get("create_time")));
        target.put("updateTime", firstNonNull(source.get("updated_at"), source.get("update_time")));
        return target;
    }

    private Integer normalizeStatus(Object statusValue, Map<String, Object> source) {
        int status = statusValue instanceof Number ? ((Number) statusValue).intValue() : -1;
        Number total = source.get("total_count") instanceof Number ? (Number) source.get("total_count") : null;
        Number finished = source.get("finished_count") instanceof Number ? (Number) source.get("finished_count") : null;
        if (status == 1 && total != null && finished != null && total.intValue() > 0 && finished.intValue() >= total.intValue()) {
            return 2;
        }
        return status;
    }

    private List<Map<String, Object>> normalizeLogs(List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("logId", item.get("log_id"));
            log.put("taskId", stringValue(item.get("task_id")));
            log.put("shardId", stringValue(item.get("shard_id")));
            log.put("workerId", stringValue(item.get("worker_id")));
            log.put("level", stringValue(item.get("level")));
            log.put("message", repairText(stringValue(item.get("message"))));
            log.put("createTime", firstNonNull(item.get("create_time"), item.get("created_at")));
            result.add(log);
        }
        return result;
    }

    private List<String> normalizeStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    result.add(repairText(String.valueOf(item).trim()));
                }
            }
        } else if (value != null && !String.valueOf(value).trim().isEmpty()) {
            result.add(repairText(String.valueOf(value).trim()));
        }
        return result;
    }

    private List<String> toSingleItemList(String raw) {
        List<String> result = new ArrayList<>();
        String value = blankToNull(raw);
        if (value != null) {
            result.add(value);
        }
        return result.isEmpty() ? null : result;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : new ArrayList<>();
    }

    private Map<String, Object> mapOf(Object... args) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            map.put(String.valueOf(args[i]), args[i + 1]);
        }
        return map;
    }

    private String repairText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return countCjk(repaired) > countCjk(value) ? repaired : value;
    }

    private int countCjk(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                count++;
            }
        }
        return count;
    }
}
