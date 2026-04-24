package com.career.platform.crawl.scheduler;

import com.career.platform.crawl.service.CrawlAutomationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CrawlAutomationScheduler {

    private final CrawlAutomationService crawlAutomationService;

    public CrawlAutomationScheduler(CrawlAutomationService crawlAutomationService) {
        this.crawlAutomationService = crawlAutomationService;
    }

    @Scheduled(initialDelayString = "${career.crawl.automation.poll-initial-delay-ms:15000}",
            fixedDelayString = "${career.crawl.automation.poll-interval-ms:60000}")
    public void dispatchScheduledCollection() {
        crawlAutomationService.pollAndTriggerScheduledCollection();
    }
}
