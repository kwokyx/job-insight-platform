package com.career.platform.crawl.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.crawl.service.CrawlAutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Crawl Automation", description = "Manage local auth scripts and scheduled collection orchestration")
@RestController
@RequestMapping("/api/v1/crawl/automation")
@PreAuthorize("hasRole('ADMIN')")
public class CrawlAutomationController {

    private final CrawlAutomationService crawlAutomationService;

    public CrawlAutomationController(CrawlAutomationService crawlAutomationService) {
        this.crawlAutomationService = crawlAutomationService;
    }

    @Operation(summary = "Automation status")
    @GetMapping("/status")
    public R<?> status() {
        return R.ok(crawlAutomationService.getAutomationStatus());
    }

    @Log("Update crawl automation config")
    @Operation(summary = "Update automation config")
    @PutMapping("/config")
    public R<?> updateConfig(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok("Automation config updated", crawlAutomationService.updateAutomationConfig(request == null ? java.util.Collections.<String, Object>emptyMap() : request));
    }

    @Log("Trigger scheduled collection orchestration")
    @Operation(summary = "Trigger collection now")
    @PostMapping("/trigger")
    public R<?> trigger(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok("Automation triggered", crawlAutomationService.triggerConfiguredCollection(
                "MANUAL_PLATFORM",
                request == null ? java.util.Collections.<String, Object>emptyMap() : request
        ));
    }

    @Log("Queue auth watchdog")
    @Operation(summary = "Queue zhaopin auth watchdog")
    @PostMapping("/watchdog")
    public R<?> queueWatchdog() {
        return R.ok("Watchdog queued", crawlAutomationService.queueWatchdogCommand("MANUAL_PLATFORM"));
    }

    @Log("Queue auth snapshot sync")
    @Operation(summary = "Queue auth snapshot sync")
    @PostMapping("/auth-sync")
    public R<?> queueAuthSync() {
        return R.ok("Auth sync queued", crawlAutomationService.queueSyncAuthCommand("MANUAL_PLATFORM"));
    }
}
