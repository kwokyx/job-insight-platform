package com.career.platform.warehouse.scheduler;

import com.career.platform.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据仓库定时调度器
 * 每天凌晨02:00执行全量ETL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseScheduler {

    private final WarehouseService warehouseService;

    /**
     * 每天凌晨 02:00 执行 ETL
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyEtl() {
        log.info("定时 ETL 调度触发");
        try {
            warehouseService.runFullEtl();
        } catch (Exception e) {
            log.error("定时 ETL 执行失败", e);
        }
    }
}
