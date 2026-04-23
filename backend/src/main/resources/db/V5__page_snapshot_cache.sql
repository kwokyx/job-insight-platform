CREATE TABLE IF NOT EXISTS `ads_page_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `page_code` varchar(64) NOT NULL COMMENT 'Snapshot code',
  `page_name` varchar(100) NOT NULL COMMENT 'Snapshot name',
  `payload_json` json NOT NULL COMMENT 'Snapshot payload',
  `refresh_trigger` varchar(64) DEFAULT NULL COMMENT 'Refresh trigger',
  `refreshed_at` datetime DEFAULT NULL COMMENT 'Refreshed at',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ads_page_snapshot_code` (`page_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Page-level snapshot cache';
