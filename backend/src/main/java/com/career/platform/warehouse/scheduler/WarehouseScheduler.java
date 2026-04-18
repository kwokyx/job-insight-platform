package com.career.platform.warehouse.scheduler;

import com.career.platform.warehouse.service.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据仓库定时调度器
 * 调度策略：
 *   - 增量 ETL：每小时执行，只处理新增/变更记录（高效）
 *   - 全量 ETL：每天凌晨 02:00 执行，保证数据完整性
 */
@Component
public class WarehouseScheduler {

    private static final Logger log = LoggerFactory.getLogger(WarehouseScheduler.class);

    private final WarehouseService warehouseService;

    public WarehouseScheduler(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /**
     * 增量 ETL — 每小时整点执行
     * 只处理 updated_at > lastEtlTime 的记录，耗时远低于全量 ETL
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyIncrementalEtl() {
        log.info("[调度] 增量 ETL 触发");
        try {
            warehouseService.runIncrementalEtl();
        } catch (Exception e) {
            log.error("[调度] 增量 ETL 执行失败", e);
        }
    }

    /**
     * 全量 ETL — 每天凌晨 02:00 执行
     * 重建所有层，保证数据完整性和历史一致性
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyFullEtl() {
        log.info("[调度] 全量 ETL 触发");
        try {
            warehouseService.runFullEtl();
        } catch (Exception e) {
            log.error("[调度] 全量 ETL 执行失败", e);
        }
    }
}
