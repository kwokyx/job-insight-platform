package com.career.platform.crawl.service;

import com.career.platform.crawl.config.CrawlAutomationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CrawlAutomationService {

    private static final Logger log = LoggerFactory.getLogger(CrawlAutomationService.class);
    private static final DateTimeFormatter TASK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CrawlAutomationProperties properties;
    private final CrawlSchedulerGateway crawlSchedulerGateway;
    private final ZhaopinAuthWatchdogService authWatchdogService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CrawlAutomationService(CrawlAutomationProperties properties,
                                  CrawlSchedulerGateway crawlSchedulerGateway,
                                  ZhaopinAuthWatchdogService authWatchdogService) {
        this.properties = properties;
        this.crawlSchedulerGateway = crawlSchedulerGateway;
        this.authWatchdogService = authWatchdogService;
    }

    public Map<String, Object> triggerConfiguredCollection(String triggerSource) {
        if (!running.compareAndSet(false, true)) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("triggerSource", triggerSource);
            payload.put("status", "SKIPPED");
            payload.put("message", "已有采集编排正在执行，跳过本次触发");
            return payload;
        }

        try {
            Map<String, Object> authResult = new LinkedHashMap<String, Object>();
            if ("zhaopin".equalsIgnoreCase(properties.getChannel()) && properties.isWatchdogEnabled()) {
                authResult = authWatchdogService.ensureAuthReady();
            }

            Map<String, Object> taskPayload = buildTaskPayload();
            Map<String, Object> schedulerResult = crawlSchedulerGateway.createTask(taskPayload);

            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("triggerSource", triggerSource);
            response.put("status", "CREATED");
            response.put("taskPayload", taskPayload);
            response.put("auth", authResult);
            response.put("scheduler", schedulerResult);
            return response;
        } finally {
            running.set(false);
        }
    }

    public boolean isAutomationEnabled() {
        return properties.isEnabled();
    }

    public String getCron() {
        return properties.getCron();
    }

    private Map<String, Object> buildTaskPayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("task_name", properties.getTaskNamePrefix() + "-" + TASK_TIME_FORMAT.format(LocalDateTime.now()));
        payload.put("channel", properties.getChannel());
        putListIfPresent(payload, "keywords", properties.getKeywords());
        putListIfPresent(payload, "city", properties.getCities());
        payload.put("page_count", properties.getPageCount());
        payload.put("priority", properties.getPriority());
        payload.put("schedule_type", "IMMEDIATE");
        payload.put("incremental", properties.isIncremental());
        payload.put("incremental_page_limit", properties.getIncrementalPageLimit());
        payload.put("stale_page_threshold", properties.getStalePageThreshold());
        payload.put("lookback_hours", properties.getLookbackHours());
        payload.put("create_user", properties.getCreateUser());
        return payload;
    }

    private void putListIfPresent(Map<String, Object> payload, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            payload.put(key, values);
        }
    }
}
