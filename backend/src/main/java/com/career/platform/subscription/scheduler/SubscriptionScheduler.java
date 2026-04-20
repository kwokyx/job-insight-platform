package com.career.platform.subscription.scheduler;

import com.career.platform.subscription.service.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final PushService pushService;

    public SubscriptionScheduler(PushService pushService) {
        this.pushService = pushService;
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void dispatchDailySubscriptions() {
        try {
            int delivered = pushService.dispatchAllActiveSubscriptions(10);
            log.info("Daily subscription dispatch completed, delivered {}", delivered);
        } catch (Exception e) {
            log.error("Daily subscription dispatch failed", e);
        }
    }
}
