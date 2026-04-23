package com.career.platform.warehouse.scheduler;

import com.career.platform.warehouse.service.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WarehouseScheduler {

    private static final Logger log = LoggerFactory.getLogger(WarehouseScheduler.class);

    private final WarehouseService warehouseService;

    public WarehouseScheduler(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyIncrementalEtl() {
        log.info("[调度] 增量 ETL 触发");
        try {
            warehouseService.runIncrementalEtl();
        } catch (Exception e) {
            log.error("[调度] 增量 ETL 执行失败", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void dailyFullEtl() {
        log.info("[调度] 全量 ETL 触发");
        try {
            warehouseService.runFullEtl();
            warehouseService.refreshPageSnapshots(null, "SCHEDULED_04_00");
        } catch (Exception e) {
            log.error("[调度] 全量 ETL 执行失败", e);
        }
    }
}
