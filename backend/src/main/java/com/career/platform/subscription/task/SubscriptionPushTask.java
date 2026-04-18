package com.career.platform.subscription.task;

import com.career.platform.subscription.service.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPushTask {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPushTask.class);

    private final PushService pushService;

    public SubscriptionPushTask(PushService pushService) {
        this.pushService = pushService;
    }

    /**
     * 每天早上 9 点触发，为所有活跃订阅用户推送匹配岗位
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void executePush() {
        log.info("Starting daily job push task...");
        try {
            // 每个订阅最多推送10条岗位
            int count = pushService.dispatchAllActiveSubscriptions(10);
            log.info("Daily job push task completed successfully. Total matched jobs pushed: {}", count);
        } catch (Exception e) {
            log.error("Error occurred during daily job push task", e);
        }
    }
}
