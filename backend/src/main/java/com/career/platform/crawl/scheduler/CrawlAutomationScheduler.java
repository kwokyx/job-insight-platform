package com.career.platform.crawl.scheduler;

import com.career.platform.crawl.service.CrawlAutomationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CrawlAutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlAutomationScheduler.class);

    private final CrawlAutomationService crawlAutomationService;

    public CrawlAutomationScheduler(CrawlAutomationService crawlAutomationService) {
        this.crawlAutomationService = crawlAutomationService;
    }

    @Scheduled(cron = "${career.crawl.automation.cron:0 0 7,13,19 * * ?}")
    public void dispatchScheduledCollection() {
        if (!crawlAutomationService.isAutomationEnabled()) {
            return;
        }
        try {
            crawlAutomationService.triggerConfiguredCollection("SPRING_SCHEDULED");
        } catch (Exception e) {
            log.error("定时采集编排执行失败", e);
        }
    }
}
