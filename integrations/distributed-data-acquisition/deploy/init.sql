-- 分布式数据采集模块数据库初始化脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS crawler_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crawler_db;

-- 采集任务主表
CREATE TABLE IF NOT EXISTS crawl_task (
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID，UUID',
    parent_task_id VARCHAR(64) NULL COMMENT '父任务ID',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    channel VARCHAR(32) NOT NULL COMMENT '采集渠道: zhaopin/boss等',
    keywords TEXT NULL COMMENT '搜索关键词，JSON数组',
    city VARCHAR(100) NULL COMMENT '采集城市，JSON数组',
    page_count INT NOT NULL DEFAULT 3 COMMENT '每个关键词/城市/分类组合采集页数',
    schedule_type VARCHAR(32) NOT NULL DEFAULT 'IMMEDIATE' COMMENT '调度类型: IMMEDIATE/SCHEDULED_TEMPLATE/SCHEDULED_RUN',
    cron_expression VARCHAR(100) NULL COMMENT 'Cron表达式',
    schedule_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '调度时区',
    schedule_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '定时任务是否启用',
    schedule_preset VARCHAR(20) NULL COMMENT '简单频率: DAILY/WEEKLY/MONTHLY',
    schedule_time VARCHAR(10) NULL COMMENT '简单频率时间 HH:mm',
    schedule_days TEXT NULL COMMENT '周几或每月日期，JSON数组',
    next_run_time DATETIME NULL COMMENT '下一次执行时间',
    last_run_time DATETIME NULL COMMENT '上一次触发时间',
    incremental TINYINT NOT NULL DEFAULT 0 COMMENT '是否增量采集',
    baseline_task_id VARCHAR(64) NULL COMMENT '基线任务ID',
    incremental_page_limit INT NOT NULL DEFAULT 2 COMMENT '增量采集页数',
    stale_page_threshold INT NOT NULL DEFAULT 2 COMMENT '连续旧页阈值',
    lookback_hours INT NOT NULL DEFAULT 72 COMMENT '发布时间回看小时数',
    last_success_at DATETIME NULL COMMENT '最近一次成功完成时间',
    watermark_publish_date DATETIME NULL COMMENT '发布时间水位线',
    watermark_crawl_time DATETIME NULL COMMENT '采集时间水位线',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待执行 1运行中 2完成 3失败',
    priority INT NOT NULL DEFAULT 5 COMMENT '调度优先级，数值越小优先级越高',
    total_count INT NOT NULL DEFAULT 0 COMMENT '总任务数',
    finished_count INT NOT NULL DEFAULT 0 COMMENT '完成数',
    new_count INT NOT NULL DEFAULT 0 COMMENT '新增职位数',
    updated_count INT NOT NULL DEFAULT 0 COMMENT '更新职位数',
    duplicate_count INT NOT NULL DEFAULT 0 COMMENT '去重数',
    start_time DATETIME NULL COMMENT '任务启动时间',
    end_time DATETIME NULL COMMENT '任务结束时间',
    create_user VARCHAR(50) NULL COMMENT '任务创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (task_id),
    INDEX idx_status (status),
    INDEX idx_channel (channel),
    INDEX idx_created_at (created_at),
    INDEX idx_parent_task_id (parent_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集任务主表';

-- 任务分片表
CREATE TABLE IF NOT EXISTS crawl_task_shard (
    shard_id VARCHAR(64) NOT NULL COMMENT '分片唯一标识',
    task_id VARCHAR(64) NOT NULL COMMENT '关联主任务',
    page INT NOT NULL COMMENT '采集页码',
    keyword VARCHAR(100) NULL COMMENT '单分片关键词',
    city VARCHAR(100) NULL COMMENT '单分片城市',
    category_code VARCHAR(50) NULL COMMENT '职位分类编码',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待执行 1运行中 2完成 3失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    stop_reason VARCHAR(50) NULL COMMENT '提前终止原因',
    new_count INT NOT NULL DEFAULT 0 COMMENT '新增职位数',
    updated_count INT NOT NULL DEFAULT 0 COMMENT '更新职位数',
    duplicate_count INT NOT NULL DEFAULT 0 COMMENT '重复职位数',
    worker_id VARCHAR(64) NULL COMMENT '执行该分片的爬虫节点',
    start_time DATETIME NULL COMMENT '分片执行开始时间',
    end_time DATETIME NULL COMMENT '分片执行完成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (shard_id),
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_worker_id (worker_id),
    INDEX idx_start_time (start_time),
    UNIQUE INDEX uq_shard_task_page (task_id, page, keyword, city, category_code),
    FOREIGN KEY (task_id) REFERENCES crawl_task(task_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务分片表';

-- 爬虫节点表
CREATE TABLE IF NOT EXISTS crawl_worker (
    worker_id VARCHAR(64) NOT NULL COMMENT '爬虫节点唯一标识',
    ip VARCHAR(50) NOT NULL COMMENT '节点服务器IP',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0离线 1在线',
    current_task_id VARCHAR(64) NULL COMMENT '节点正在执行的任务',
    cpu_usage INT NOT NULL DEFAULT 0 COMMENT 'CPU使用率百分比',
    memory_usage INT NOT NULL DEFAULT 0 COMMENT '内存使用率百分比',
    last_heartbeat DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '节点心跳上报时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (worker_id),
    INDEX idx_status (status),
    INDEX idx_last_heartbeat (last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='爬虫节点表';

-- 代理IP池表
CREATE TABLE IF NOT EXISTS crawl_proxy (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    proxy_ip VARCHAR(50) NOT NULL COMMENT '代理服务器IP:端口',
    protocol VARCHAR(10) NOT NULL DEFAULT 'http' COMMENT '协议: http/https',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0不可用 1可用',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '失败次数',
    success_rate INT NOT NULL DEFAULT 100 COMMENT '成功率百分比',
    avg_response_time INT NOT NULL DEFAULT 0 COMMENT '平均响应时间(ms)',
    last_used_time DATETIME NULL COMMENT '代理最后使用时间',
    worker_id VARCHAR(64) NULL COMMENT '绑定的爬虫节点',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_status (status),
    INDEX idx_worker_id (worker_id),
    INDEX idx_success_rate (success_rate),
    UNIQUE INDEX uniq_proxy_ip (proxy_ip),
    FOREIGN KEY (worker_id) REFERENCES crawl_worker(worker_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代理IP池表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    config_key VARCHAR(100) NOT NULL COMMENT '配置项唯一键',
    config_value TEXT NOT NULL COMMENT '配置项内容',
    channel VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '渠道: default/zhaopin/boss',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    expire_time DATETIME NULL COMMENT '配置有效期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uniq_config_key (config_key),
    INDEX idx_channel (channel),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 任务日志表
CREATE TABLE IF NOT EXISTS crawl_task_log (
    log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    task_id VARCHAR(64) NOT NULL COMMENT '关联采集任务',
    shard_id VARCHAR(64) NULL COMMENT '关联任务分片',
    worker_id VARCHAR(64) NULL COMMENT '关联爬虫节点',
    level VARCHAR(10) NOT NULL DEFAULT 'INFO' COMMENT '日志级别: DEBUG/INFO/WARN/ERROR',
    message TEXT NOT NULL COMMENT '日志详细信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (log_id),
    INDEX idx_task_id (task_id),
    INDEX idx_shard_id (shard_id),
    INDEX idx_worker_id (worker_id),
    INDEX idx_level (level),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (task_id) REFERENCES crawl_task(task_id) ON DELETE CASCADE,
    FOREIGN KEY (shard_id) REFERENCES crawl_task_shard(shard_id) ON DELETE SET NULL,
    FOREIGN KEY (worker_id) REFERENCES crawl_worker(worker_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日志表';

-- 职位数据表
CREATE TABLE IF NOT EXISTS job_data (
    url VARCHAR(500) NULL COMMENT '招聘职位原始页面链接',
    url_obj_id VARCHAR(100) NOT NULL COMMENT '来源平台职位唯一标识，可用于去重',
    title VARCHAR(200) NOT NULL COMMENT '招聘职位名称',
    salary_min INT NULL DEFAULT 0 COMMENT '最低薪资',
    salary_max INT NULL DEFAULT 0 COMMENT '最高薪资',
    salary_raw VARCHAR(50) NULL COMMENT '页面原始薪资格式',
    job_city VARCHAR(50) NOT NULL COMMENT '职位工作城市',
    district VARCHAR(50) NULL COMMENT '工作区县',
    street_name VARCHAR(100) NULL COMMENT '街道',
    experience_year VARCHAR(50) NULL COMMENT '工作经验要求',
    education_need VARCHAR(50) NULL COMMENT '学历要求',
    publish_date DATETIME NULL COMMENT '职位发布时间',
    job_welfare TEXT NULL COMMENT '页面提取的福利信息',
    job_labels TEXT NULL COMMENT '页面提取的标签信息，原始存储',
    job_skill_tags TEXT NULL COMMENT '技能标签',
    job_keywords TEXT NULL COMMENT '合并去重后的关键词标签',
    requirements TEXT NULL COMMENT '任职要求',
    position_info TEXT NULL COMMENT '岗位职责与任职要求原文',
    job_classification VARCHAR(100) NULL COMMENT '页面原始职位分类',
    address VARCHAR(500) NULL COMMENT '企业地址',
    company_name VARCHAR(100) NULL COMMENT '招聘公司名称',
    company_size VARCHAR(50) NULL COMMENT '公司规模信息',
    company_type VARCHAR(50) NULL COMMENT '企业性质',
    company_finance VARCHAR(50) NULL COMMENT '公司融资阶段或融资信息',
    industry_name VARCHAR(100) NULL COMMENT '公司行业',
    industry_code VARCHAR(50) NULL COMMENT '行业编码',
    company_url VARCHAR(500) NULL COMMENT '公司主页',
    crawl_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据采集时间',
    crawl_update_time DATETIME NULL COMMENT '本条采集记录更新时间',
    company_logo VARCHAR(500) NULL COMMENT '公司logo图片URL',
    task_id VARCHAR(64) NULL COMMENT '关联采集任务',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (url_obj_id),
    INDEX idx_task_id (task_id),
    INDEX idx_job_city (job_city),
    INDEX idx_crawl_time (crawl_time),
    INDEX idx_salary_min (salary_min),
    INDEX idx_company_name (company_name),
    INDEX idx_publish_date (publish_date),
    FOREIGN KEY (task_id) REFERENCES crawl_task(task_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职位数据表';

-- 插入初始配置
INSERT IGNORE INTO system_config (config_key, config_value, channel, status) VALUES
-- 智联招聘Token配置（需要定期更新）
('zhaopin.token.mme', 'MmEwMD=', 'zhaopin', 1),
('zhaopin.token.c1k', 'c1K5tw0w6_=', 'zhaopin', 1),
-- 爬虫配置
('crawler.max_retry_count', '3', 'default', 1),
('crawler.download_delay', '2.0', 'default', 1),
('crawler.concurrent_requests', '8', 'default', 1),
-- 代理配置
('proxy.enabled', 'false', 'default', 1),
('proxy.pool.url', 'http://proxy-pool:5010/get', 'default', 1),
-- 调度配置
('scheduler.heartbeat_timeout', '90', 'default', 1),
('scheduler.heartbeat_interval', '30', 'default', 1),
('scheduler.shard_max_pages', '100', 'default', 1);

-- 创建定时任务清理旧日志（事件调度需要开启）
DELIMITER //
CREATE EVENT IF NOT EXISTS cleanup_old_logs
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
BEGIN
    -- 删除30天前的日志
    DELETE FROM crawl_task_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
    -- 删除90天前的职位数据（可根据需要调整）
    -- DELETE FROM job_data WHERE crawl_time < DATE_SUB(NOW(), INTERVAL 90 DAY);
END //
DELIMITER ;

-- 启用事件调度器
SET GLOBAL event_scheduler = ON;

-- 创建存储过程：获取可用节点
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS get_available_workers()
BEGIN
    SELECT * FROM crawl_worker
    WHERE status = 1
    AND last_heartbeat > DATE_SUB(NOW(), INTERVAL 90 SECOND)
    ORDER BY cpu_usage ASC, memory_usage ASC;
END //
DELIMITER ;

-- 创建视图：任务统计视图
CREATE OR REPLACE VIEW task_stats_view AS
SELECT
    t.task_id,
    t.task_name,
    t.channel,
    t.status,
    t.total_count,
    t.finished_count,
    t.duplicate_count,
    COUNT(s.shard_id) as total_shards,
    SUM(CASE WHEN s.status = 0 THEN 1 ELSE 0 END) as pending_shards,
    SUM(CASE WHEN s.status = 1 THEN 1 ELSE 0 END) as running_shards,
    SUM(CASE WHEN s.status = 2 THEN 1 ELSE 0 END) as completed_shards,
    SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END) as failed_shards
FROM crawl_task t
LEFT JOIN crawl_task_shard s ON t.task_id = s.task_id
GROUP BY t.task_id, t.task_name, t.channel, t.status, t.total_count, t.finished_count, t.duplicate_count;

-- 创建视图：节点统计视图
CREATE OR REPLACE VIEW worker_stats_view AS
SELECT
    w.worker_id,
    w.ip,
    w.status,
    w.cpu_usage,
    w.memory_usage,
    w.last_heartbeat,
    COUNT(s.shard_id) as total_shards,
    SUM(CASE WHEN s.status = 2 THEN 1 ELSE 0 END) as completed_shards,
    SUM(CASE WHEN s.status = 3 THEN 1 ELSE 0 END) as failed_shards,
    COUNT(DISTINCT s.task_id) as total_tasks
FROM crawl_worker w
LEFT JOIN crawl_task_shard s ON w.worker_id = s.worker_id
GROUP BY w.worker_id, w.ip, w.status, w.cpu_usage, w.memory_usage, w.last_heartbeat;
