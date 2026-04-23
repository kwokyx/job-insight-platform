-- ============================================================
-- Open API 基础表 —— sys_api_key + sys_api_call_log
-- 用于支撑 /api/v1/open/** 端点的 API Key 认证与审计日志
-- ============================================================
USE career_platform;

-- ─── API Key 管理表 ───
CREATE TABLE IF NOT EXISTS `sys_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `api_key` varchar(128) NOT NULL COMMENT 'API Key (cpk_前缀 + UUID)',
  `key_name` varchar(100) NOT NULL COMMENT '密钥名称/用途说明',
  `permissions` json DEFAULT NULL COMMENT '权限配置 JSON (字段白名单等)',
  `rate_limit_qps` int NOT NULL DEFAULT 10 COMMENT 'QPS 限流阈值',
  `daily_quota` int NOT NULL DEFAULT 1000 COMMENT '每日调用配额',
  `is_active` tinyint NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间 (NULL=永不过期)',
  `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_api_key_user` (`user_id`),
  KEY `idx_api_key_active` (`is_active`),
  CONSTRAINT `fk_api_key_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Open API Key 管理表';

-- ─── API 调用审计日志表 ───
CREATE TABLE IF NOT EXISTS `sys_api_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `api_key_id` bigint DEFAULT NULL COMMENT 'API Key ID',
  `endpoint` varchar(300) NOT NULL COMMENT '请求路径',
  `method` varchar(10) NOT NULL COMMENT 'HTTP 方法',
  `request_params` varchar(500) DEFAULT NULL COMMENT '请求参数 (截断)',
  `response_code` int DEFAULT NULL COMMENT 'HTTP 响应码',
  `response_time` bigint DEFAULT NULL COMMENT '响应耗时 (ms)',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '客户端 IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求追踪 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_call_log_key` (`api_key_id`),
  KEY `idx_call_log_endpoint` (`endpoint`(191)),
  KEY `idx_call_log_created` (`created_at`),
  KEY `idx_call_log_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Open API 调用审计日志表';

-- ─── 插入默认 API Key (管理员用) ───
INSERT INTO `sys_api_key` (`user_id`, `api_key`, `key_name`, `permissions`, `rate_limit_qps`, `daily_quota`, `is_active`)
SELECT 1, 'cpk_default_demo_key_2026', '平台默认演示密钥',
       '{"fieldProfile": "extended", "tenantScope": "public"}',
       20, 5000, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_api_key` WHERE `api_key` = 'cpk_default_demo_key_2026'
);
