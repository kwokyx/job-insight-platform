package com.career.platform.crawl.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.crawl.service.CrawlSchedulerGateway;
import com.career.platform.crawl.service.CrawlRegionService;
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
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Crawl Task Management", description = "Manage crawler tasks with scheduler-center")
@RestController
@RequestMapping("/api/v1/crawl/tasks")
@PreAuthorize("hasRole('ADMIN')")
public class CrawlTaskController {

    private static final DateTimeFormatter TASK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CrawlSchedulerGateway crawlSchedulerGateway;
    private final DataQualityService dataQualityService;
    private final CrawlRegionService crawlRegionService;
    private final WarehouseService warehouseService;

    public CrawlTaskController(CrawlSchedulerGateway crawlSchedulerGateway,
                               DataQualityService dataQualityService,
                               CrawlRegionService crawlRegionService,
                               WarehouseService warehouseService) {
        this.crawlSchedulerGateway = crawlSchedulerGateway;
        this.dataQualityService = dataQualityService;
        this.crawlRegionService = crawlRegionService;
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "Crawler task list")
    @GetMapping
    public R<?> listTasks(@RequestParam(required = false) String channel,
                          @RequestParam(required = false) Integer status,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize) {
        List<Map<String, Object>> tasks = loadSortedTasks(channel, status);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, tasks.size());
        int toIndex = Math.min(fromIndex + safePageSize, tasks.size());
        return R.page(tasks.subList(fromIndex, toIndex), tasks.size(), safePage, safePageSize);
    }

    public static class CreateTaskRequest {
        private String taskName;
        @NotBlank(message = "channel is required")
        private String channel;
        private String keywords;
        private String city;
        private Integer targetCount;
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
        public Integer getTargetCount() { return targetCount; }
        public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
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
        payload.put("task_name", resolveTaskName(req));
        payload.put("channel", req.getChannel());
        payload.put("keywords", toSingleItemList(req.getKeywords()));
        payload.put("city", toCityList(req.getCity()));
        payload.put("priority", req.getPriority() == null ? 5 : req.getPriority());
        payload.put("page_count", resolvePageCount(req.getPageCount(), req.getTargetCount()));
        payload.put("target_count", resolveTargetCount(req.getTargetCount(), req.getPageCount()));
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
        result.put("pageCount", resolvePageCount(req.getPageCount(), req.getTargetCount()));
        result.put("targetCount", resolveTargetCount(req.getTargetCount(), req.getPageCount()));
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

    @Operation(summary = "Realtime crawl overview")
    @GetMapping("/live")
    public R<?> liveOverview() {
        List<Map<String, Object>> tasks = loadSortedTasks(null, null);
        List<Map<String, Object>> visibleTasks = new ArrayList<>();
        List<Map<String, Object>> runningTasks = new ArrayList<>();
        List<Map<String, Object>> failedTasks = new ArrayList<>();
        for (Map<String, Object> task : tasks) {
            if (isProbeTask(task) || isScheduledTemplate(task)) {
                continue;
            }
            visibleTasks.add(task);
            Integer status = task.get("status") instanceof Number ? ((Number) task.get("status")).intValue() : null;
            if ((status != null && status == 1) || hasRecentIncompleteProgress(task) || looksRealtimeInFlight(task)) {
                runningTasks.add(task);
            }
            if (status != null && status == 3 && !looksRealtimeInFlight(task)) {
                failedTasks.add(task);
            }
        }

        List<Map<String, Object>> latestLogs = loadLatestLogs(visibleTasks);
        Map<String, Object> focusTask = selectRealtimeFocusTask(visibleTasks);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", warehouseService.crawlRealtimeSummary());
        result.put("runningTasks", runningTasks);
        result.put("failedTasks", failedTasks);
        result.put("latestLogs", latestLogs);
        result.put("latestTask", focusTask);
        result.put("activeProgress", buildActiveProgress(visibleTasks, latestLogs, focusTask));
        return R.ok(result);
    }

    private List<Map<String, Object>> loadSortedTasks(String channel, Integer status) {
        final int pageSize = 100;
        final int maxPages = 10;
        List<Map<String, Object>> rawItems = new ArrayList<>();
        long total = 0;
        for (int currentPage = 1; currentPage <= maxPages; currentPage++) {
            Map<String, Object> query = new LinkedHashMap<>();
            if (channel != null) {
                query.put("channel", channel);
            }
            if (status != null) {
                query.put("status", status);
            }
            query.put("page", currentPage);
            query.put("size", pageSize);
            Map<String, Object> response = crawlSchedulerGateway.listTasks(query);
            Map<String, Object> data = requireData(response);
            List<Map<String, Object>> pageItems = castList(data.get("items"));
            rawItems.addAll(pageItems);
            total = Math.max(total, asLong(data.get("total")));
            if (rawItems.size() >= total || pageItems.isEmpty()) {
                break;
            }
        }
        return normalizeTaskList(rawItems);
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
            Map<String, Object> normalized = normalizeTask(item);
            if (!isProbeTask(normalized)) {
                result.add(normalized);
            }
        }
        result.sort(
                Comparator.comparingInt(this::taskStatusRank)
                        .thenComparing(this::taskSortKey, Comparator.reverseOrder())
        );
        return result;
    }

    private boolean isProbeTask(Map<String, Object> task) {
        String taskId = stringValue(task.get("taskId"));
        String taskName = repairText(stringValue(task.get("taskName")));
        String createUser = stringValue(task.get("createUser"));
        return (taskId != null && taskId.startsWith("probe_"))
                || (taskName != null && taskName.toLowerCase().contains("probe"))
                || "system-probe".equalsIgnoreCase(createUser)
                || "probe".equalsIgnoreCase(createUser);
    }

    private Map<String, Object> normalizeTask(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("taskId", stringValue(source.get("task_id")));
        target.put("parentTaskId", stringValue(source.get("parent_task_id")));
        target.put("taskName", normalizeTaskName(source));
        target.put("channel", stringValue(source.get("channel")));
        target.put("keywords", normalizeStringList(source.get("keywords")));
        target.put("city", normalizeRegionList(source.get("city")));
        target.put("pageCount", source.get("page_count"));
        target.put("targetCount", resolveTargetCount(intOrNull(source.get("target_count")), intOrNull(source.get("page_count"))));
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

    private String normalizeTaskName(Map<String, Object> source) {
        String taskName = repairText(stringValue(source.get("task_name")));
        if (taskName != null && !taskName.trim().isEmpty() && !looksBrokenText(taskName)) {
            return taskName;
        }

        List<String> keywords = normalizeStringList(source.get("keywords"));
        List<String> cities = normalizeRegionList(source.get("city"));
        String keywordText = keywords.isEmpty() ? "采集" : String.join("/", keywords);
        String cityText = cities.isEmpty() ? "多城市" : String.join("/", cities);
        String startTime = stringValue(source.get("start_time"));
        if (startTime == null || startTime.trim().isEmpty()) {
            startTime = stringValue(source.get("create_time"));
        }
        String timeSuffix = "";
        if (startTime != null && startTime.length() >= 16) {
            timeSuffix = startTime.substring(0, 16).replace(" ", " ");
        }
        return timeSuffix.isEmpty()
                ? keywordText + "-" + cityText + "-采集任务"
                : keywordText + "-" + cityText + "-" + timeSuffix;
    }

    private Integer normalizeStatus(Object statusValue, Map<String, Object> source) {
        int status = statusValue instanceof Number ? ((Number) statusValue).intValue() : -1;
        String scheduleType = stringValue(source.get("schedule_type"));
        if ("SCHEDULED_TEMPLATE".equalsIgnoreCase(scheduleType)) {
            return status;
        }
        Number total = source.get("total_count") instanceof Number ? (Number) source.get("total_count") : null;
        Number finished = source.get("finished_count") instanceof Number ? (Number) source.get("finished_count") : null;
        int totalCount = total == null ? 0 : total.intValue();
        int finishedCount = finished == null ? 0 : finished.intValue();
        if (status == 3) {
            if (isRecentSourceTask(source, 15) && (totalCount <= 0 || finishedCount < totalCount)) {
                return 1;
            }
            if (totalCount > 0 && finishedCount >= totalCount) {
                return 2;
            }
        }
        if (status == 1 && total != null && finished != null && total.intValue() > 0 && finished.intValue() >= total.intValue()) {
            return 2;
        }
        if ((status == 0 || status == 1) && isStaleActiveTask(source, total, finished)) {
            return 3;
        }
        return status;
    }

    private boolean isRecentSourceTask(Map<String, Object> source, int minutes) {
        LocalDateTime activityTime = parseTaskTime(
                stringValue(firstNonNull(source.get("update_time"), source.get("updated_at"))),
                stringValue(source.get("start_time")),
                stringValue(firstNonNull(source.get("create_time"), source.get("created_at")))
        );
        return activityTime != null && activityTime.isAfter(LocalDateTime.now().minusMinutes(minutes));
    }

    private boolean isStaleActiveTask(Map<String, Object> source, Number total, Number finished) {
        int totalCount = total == null ? 0 : total.intValue();
        int finishedCount = finished == null ? 0 : finished.intValue();
        LocalDateTime activityTime = parseTaskTime(
                stringValue(firstNonNull(source.get("update_time"), source.get("updated_at"))),
                stringValue(source.get("start_time")),
                stringValue(firstNonNull(source.get("create_time"), source.get("created_at")))
        );
        if (activityTime == null) {
            return false;
        }
        if (totalCount <= 0 && finishedCount <= 0) {
            return activityTime.isBefore(LocalDateTime.now().minusMinutes(30));
        }
        if (totalCount > 0 && finishedCount < totalCount) {
            return activityTime.isBefore(LocalDateTime.now().minusMinutes(90));
        }
        return false;
    }

    private LocalDateTime parseTaskTime(String... values) {
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String candidate = value.trim();
            try {
                return LocalDateTime.parse(candidate, TASK_TIME_FORMAT);
            } catch (Exception ignored) {
            }
            try {
                return LocalDateTime.parse(candidate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignored) {
            }
            try {
                return OffsetDateTime.parse(candidate).toLocalDateTime();
            } catch (Exception ignored) {
            }
        }
        return null;
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

    private List<Map<String, Object>> loadLatestLogs(List<Map<String, Object>> tasks) {
        if (tasks != null) {
            for (Map<String, Object> task : tasks) {
                String taskId = stringValue(task.get("taskId"));
                if (taskId == null || taskId.trim().isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> taskLogs = fetchLogs(mapOf(
                        "task_id", taskId,
                        "page", 1,
                        "size", 8
                ));
                if (!taskLogs.isEmpty()) {
                    return taskLogs;
                }
            }
        }
        return fetchLogs(mapOf(
                "page", 1,
                "size", 8
        ));
    }

    private List<Map<String, Object>> fetchLogs(Map<String, Object> query) {
        try {
            Map<String, Object> response = crawlSchedulerGateway.taskLogs(query);
            Map<String, Object> data = requireData(response);
            return normalizeLogs(castList(data.get("items")));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> buildActiveProgress(List<Map<String, Object>> tasks,
                                                    List<Map<String, Object>> latestLogs,
                                                    Map<String, Object> focusTask) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        Map<String, Object> targetTask = focusTask != null ? focusTask : selectRealtimeFocusTask(tasks);
        if (targetTask == null) {
            return null;
        }

        String taskId = stringValue(targetTask.get("taskId"));
        Map<String, Object> latestLog = findLatestLogForTask(taskId, latestLogs);
        Integer taskStatus = targetTask.get("status") instanceof Number ? ((Number) targetTask.get("status")).intValue() : null;
        Map<String, Object> stats = fetchTaskStats(taskId);
        int pendingShards = intValue(stats.get("pending_shards"));
        int activeShards = intValue(stats.get("running_shards"));
        int completedShardCount = intValue(stats.get("completed_shards"));
        int failedShardCount = intValue(stats.get("failed_shards"));
        int totalShards = intValue(stats.get("total_shards"));
        int finishedCount = intValue(targetTask.get("finishedCount"));
        int totalCount = intValue(targetTask.get("totalCount"));
        boolean schedulerInFlight = activeShards > 0
                || pendingShards > 0
                || (completedShardCount > 0 && totalCount > 0 && finishedCount < totalCount)
                || looksRealtimeInFlight(targetTask);

        if (taskStatus != null && taskStatus != 0 && taskStatus != 1 && !schedulerInFlight) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("taskName", targetTask.get("taskName"));
            result.put("status", targetTask.get("status"));
            result.put("stageKey", taskStatus == 2 ? "completed" : "failed");
            result.put("stageLabel", taskStatus == 2 ? "已完成" : "异常结束");
            result.put("stageDetail", taskStatus == 2
                    ? "最近一次采集已经完成，页面继续保留本次结果与日志。"
                    : "最近一次采集已结束，但存在失败或暂停分片，请直接查看失败原因。");
            result.put("keywords", targetTask.get("keywords"));
            result.put("city", targetTask.get("city"));
            result.put("finishedCount", intValue(targetTask.get("finishedCount")));
            result.put("totalCount", intValue(targetTask.get("totalCount")));
            result.put("shardStats", mapOf());
            result.put("activeShards", new ArrayList<>());
            result.put("completedShards", new ArrayList<>());
            result.put("latestLog", latestLog);
            return result;
        }

        List<Map<String, Object>> runningShards = fetchTaskShards(taskId, 1);
        List<Map<String, Object>> completedShards = fetchTaskShards(taskId, 2);

        String stageKey = "queued";
        String stageLabel = "排队中";
        String stageDetail = "任务已创建，等待调度中心分发分片。";
        if (activeShards > 0 && finishedCount <= 0) {
            stageKey = "dispatching";
            stageLabel = "分发中";
            stageDetail = "调度中心正在把分片分配给采集节点。";
        } else if (activeShards > 0 || completedShardCount > 0 || finishedCount > 0) {
            stageKey = "crawling";
            stageLabel = "爬取中";
            stageDetail = "采集节点正在抓取职位详情，页面会持续刷新分片与进度。";
        } else if (totalCount > 0 && finishedCount >= totalCount) {
            stageKey = "finishing";
            stageLabel = "汇总中";
            stageDetail = "采集已完成，正在等待结果汇总或最终状态回写。";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("taskName", targetTask.get("taskName"));
        result.put("status", targetTask.get("status"));
        result.put("stageKey", stageKey);
        result.put("stageLabel", stageLabel);
        result.put("stageDetail", stageDetail);
        result.put("keywords", targetTask.get("keywords"));
        result.put("city", targetTask.get("city"));
        result.put("finishedCount", finishedCount);
        result.put("totalCount", totalCount);
        result.put("shardStats", mapOf(
                "total", totalShards,
                "pending", pendingShards,
                "running", activeShards,
                "completed", completedShardCount,
                "failed", failedShardCount
        ));
        result.put("activeShards", runningShards);
        result.put("completedShards", completedShards);
        result.put("latestLog", latestLog);
        return result;
    }

    private Map<String, Object> selectRealtimeFocusTask(List<Map<String, Object>> tasks) {
        Map<String, Object> recentActiveTask = null;
        Map<String, Object> activeFallbackTask = null;
        Map<String, Object> recentCompletedOrFailed = null;
        Map<String, Object> latestCompletedOrFailed = null;
        for (Map<String, Object> task : tasks) {
            if (isProbeTask(task) || isScheduledTemplate(task)) {
                continue;
            }
            if (looksRealtimeInFlight(task)) {
                return task;
            }
            Integer status = task.get("status") instanceof Number ? ((Number) task.get("status")).intValue() : null;
            if (status != null && isRealtimeActiveCandidate(task, status)) {
                if (activeFallbackTask == null) {
                    activeFallbackTask = task;
                }
                if (recentActiveTask == null && isRecentTask(task, 10)) {
                    recentActiveTask = task;
                }
            }
            if (status != null && (status == 2 || status == 3)) {
                if (latestCompletedOrFailed == null) {
                    latestCompletedOrFailed = task;
                }
                if (recentCompletedOrFailed == null && isRecentTask(task, 10)) {
                    recentCompletedOrFailed = task;
                }
            }
        }
        if (recentActiveTask != null) {
            return recentActiveTask;
        }
        if (recentCompletedOrFailed != null) {
            return recentCompletedOrFailed;
        }
        if (activeFallbackTask != null) {
            return activeFallbackTask;
        }
        if (latestCompletedOrFailed != null) {
            return latestCompletedOrFailed;
        }
        for (Map<String, Object> task : tasks) {
            if (!isProbeTask(task) && !isScheduledTemplate(task)) {
                return task;
            }
        }
        return null;
    }

    private boolean isRealtimeActiveCandidate(Map<String, Object> task, Integer status) {
        if (status == null || isScheduledTemplate(task)) {
            return false;
        }
        if (hasRecentIncompleteProgress(task)) {
            return true;
        }
        if (status == 1) {
            return true;
        }
        if (status != 0) {
            return false;
        }
        return !isScheduledTemplate(task);
    }

    private boolean isScheduledTemplate(Map<String, Object> task) {
        String scheduleType = stringValue(task.get("scheduleType"));
        return "SCHEDULED_TEMPLATE".equalsIgnoreCase(scheduleType);
    }

    private boolean isRecentTask(Map<String, Object> task, int minutes) {
        LocalDateTime activityTime = parseTaskTime(
                stringValue(task.get("updateTime")),
                stringValue(task.get("endTime")),
                stringValue(task.get("startTime")),
                stringValue(task.get("createTime"))
        );
        return activityTime != null && activityTime.isAfter(LocalDateTime.now().minusMinutes(minutes));
    }

    private Map<String, Object> fetchTaskStats(String taskId) {
        try {
            Map<String, Object> response = crawlSchedulerGateway.taskStats(taskId);
            return requireData(response);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> fetchTaskShards(String taskId, int status) {
        try {
            Map<String, Object> response = crawlSchedulerGateway.taskShards(taskId, mapOf(
                    "page", 1,
                    "size", 6,
                    "status", status
            ));
            Map<String, Object> data = requireData(response);
            return normalizeShards(castList(data.get("items")));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> normalizeShards(List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> shard = new LinkedHashMap<>();
            shard.put("shardId", stringValue(item.get("shard_id")));
            shard.put("taskId", stringValue(item.get("task_id")));
            shard.put("page", item.get("page"));
            shard.put("keyword", repairText(stringValue(item.get("keyword"))));
            shard.put("city", crawlRegionService.resolveRegionDisplayName(stringValue(item.get("city"))));
            shard.put("categoryCode", stringValue(item.get("category_code")));
            shard.put("status", item.get("status"));
            shard.put("retryCount", item.get("retry_count"));
            shard.put("stopReason", repairText(stringValue(item.get("stop_reason"))));
            shard.put("newCount", item.get("new_count"));
            shard.put("updatedCount", item.get("updated_count"));
            shard.put("duplicateCount", item.get("duplicate_count"));
            shard.put("workerId", stringValue(item.get("worker_id")));
            shard.put("startTime", item.get("start_time"));
            shard.put("endTime", item.get("end_time"));
            result.add(shard);
        }
        return result;
    }

    private Map<String, Object> findLatestLogForTask(String taskId, List<Map<String, Object>> latestLogs) {
        if (taskId == null || latestLogs == null) {
            return null;
        }
        for (Map<String, Object> log : latestLogs) {
            if (taskId.equals(stringValue(log.get("taskId")))) {
                return log;
            }
        }
        List<Map<String, Object>> logs = fetchLogs(mapOf(
                "task_id", taskId,
                "page", 1,
                "size", 1
        ));
        return logs.isEmpty() ? null : logs.get(0);
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

    private List<String> normalizeRegionList(Object value) {
        List<String> values = normalizeStringList(value);
        List<String> result = new ArrayList<>();
        for (String item : values) {
            result.add(crawlRegionService.resolveRegionDisplayName(item));
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

    private List<String> toCityList(String raw) {
        List<String> result = new ArrayList<>();
        String value = blankToNull(raw);
        if (value != null) {
            String normalized = crawlRegionService.normalizeCityForScheduler(value);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String resolveTaskName(CreateTaskRequest req) {
        String taskName = blankToNull(req.getTaskName());
        if (taskName != null) {
            return taskName;
        }
        String keyword = blankToNull(req.getKeywords());
        String city = crawlRegionService.resolveRegionDisplayName(blankToNull(req.getCity()));
        int targetCount = resolveTargetCount(req.getTargetCount(), req.getPageCount());
        return String.format("%s-%s-%d条",
                keyword == null ? "采集" : keyword,
                city == null ? "多城市" : city,
                targetCount);
    }

    private int resolvePageCount(Integer pageCount, Integer targetCount) {
        if (targetCount != null && targetCount > 0) {
            return Math.max(1, (int) Math.ceil(targetCount / 10.0));
        }
        if (pageCount != null && pageCount > 0) {
            return pageCount;
        }
        return 3;
    }

    private int resolveTargetCount(Integer targetCount, Integer pageCount) {
        if (targetCount != null && targetCount > 0) {
            return targetCount;
        }
        if (pageCount != null && pageCount > 0) {
            return pageCount * 10;
        }
        return 30;
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

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
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

    private String resolveRegionName(String value) {
        return crawlRegionService.resolveRegionDisplayName(value);
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

    private boolean looksBrokenText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        if (value.startsWith("???") || value.startsWith("????") || value.contains("[\"??")) {
            return true;
        }
        int questionCount = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '?') {
                questionCount++;
            }
        }
        return questionCount > 0 && questionCount * 2 >= value.length();
    }

    private String taskSortKey(Map<String, Object> task) {
        Integer status = task.get("status") instanceof Number ? ((Number) task.get("status")).intValue() : null;
        if (status != null && status == 2) {
            return firstNonBlank(
                    stringValue(task.get("lastSuccessAt")),
                    stringValue(task.get("endTime")),
                    stringValue(task.get("watermarkCrawlTime")),
                    stringValue(task.get("startTime")),
                    stringValue(task.get("updateTime")),
                    stringValue(task.get("createTime"))
            );
        }
        if (status != null && status == 1) {
            return firstNonBlank(
                    stringValue(task.get("startTime")),
                    stringValue(task.get("updateTime")),
                    stringValue(task.get("createTime"))
            );
        }
        if (status != null && status == 0) {
            return firstNonBlank(
                    stringValue(task.get("createTime")),
                    stringValue(task.get("updateTime"))
            );
        }
        return firstNonBlank(
                stringValue(task.get("endTime")),
                stringValue(task.get("startTime")),
                stringValue(task.get("updateTime")),
                stringValue(task.get("createTime"))
        );
    }

    private int taskStatusRank(Map<String, Object> task) {
        Integer status = task.get("status") instanceof Number ? ((Number) task.get("status")).intValue() : null;
        if (status == null) {
            return 8;
        }
        if (hasRecentIncompleteProgress(task)) {
            return 0;
        }
        boolean recent = isRecentTask(task, 10);
        if (status == 1 && recent) {
            return 0;
        }
        if (status == 0 && recent) {
            return 1;
        }
        if (status == 2 && recent) {
            return 2;
        }
        if (status == 3 && recent) {
            return 3;
        }
        if (status == 2) {
            return 4;
        }
        if (status == 0) {
            return 5;
        }
        if (status == 1) {
            return 6;
        }
        if (status == 3) {
            return 7;
        }
        return 8;
    }

    private boolean hasRecentIncompleteProgress(Map<String, Object> task) {
        int totalCount = intValue(task.get("totalCount"));
        int finishedCount = intValue(task.get("finishedCount"));
        return totalCount > 0
                && finishedCount > 0
                && finishedCount < totalCount
                && isRecentTask(task, 10);
    }

    private boolean looksRealtimeInFlight(Map<String, Object> task) {
        if (task == null) {
            return false;
        }
        Integer status = task.get("status") instanceof Number ? ((Number) task.get("status")).intValue() : null;
        int totalCount = intValue(task.get("totalCount"));
        int finishedCount = intValue(task.get("finishedCount"));
        String endTime = stringValue(task.get("endTime"));
        if (status == null) {
            return false;
        }
        if (status == 0 || status == 1) {
            return true;
        }
        return status == 3
                && totalCount > 0
                && finishedCount < totalCount
                && (endTime == null || endTime.trim().isEmpty());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }
}
