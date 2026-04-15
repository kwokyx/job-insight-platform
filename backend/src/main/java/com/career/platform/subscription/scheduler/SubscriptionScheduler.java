package com.career.platform.subscription.scheduler;

import com.career.platform.subscription.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final PushService pushService;

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
