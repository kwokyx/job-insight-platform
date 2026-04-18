-- 添加数据时效性状态字段
ALTER TABLE `biz_job_posting` 
ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '岗位状态: 0-已过期(下线), 1-正常有效(默认)';

-- 为时效性治理添加索引
ALTER TABLE `biz_job_posting`
ADD INDEX `idx_status_publish` (`status`, `publish_date`);
