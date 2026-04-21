-- ============================================================
-- 正式版数据库必须补齐的表和字段
-- 说明：
-- 1. 这份脚本只包含完整系统“非做不可”的新增表和字段
-- 2. 不包含收藏、实验、日志增强等可选扩展表
-- 3. 设计目标与 DB_TABLE_SUMMARY.md / DB_FUTURE_TABLES.md 保持一致
-- ============================================================

USE career_platform;

-- ============================================================
-- 1. 地区层级表
-- ============================================================
CREATE TABLE IF NOT EXISTS dim_region (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    region_code     VARCHAR(20)  NOT NULL,
    region_name     VARCHAR(100) NOT NULL,
    region_level    TINYINT      NOT NULL COMMENT '1-大区 2-省 3-市',
    parent_code     VARCHAR(20),
    full_name       VARCHAR(200),
    sort_no         INT DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_region_code (region_code),
    INDEX idx_parent_code (parent_code),
    INDEX idx_region_level (region_level),
    CONSTRAINT fk_dim_region_parent_code
        FOREIGN KEY (parent_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地区层级表';

-- ============================================================
-- 2. 专业字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS dim_major (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    major_code      VARCHAR(50)  NOT NULL,
    major_name      VARCHAR(100) NOT NULL,
    major_category  VARCHAR(100),
    education_level VARCHAR(50),
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_major_code (major_code),
    INDEX idx_major_name (major_name),
    INDEX idx_major_category (major_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业字典表';

-- ============================================================
-- 3. 岗位类别字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS dim_job_category (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code   VARCHAR(50)  NOT NULL,
    category_name   VARCHAR(100) NOT NULL,
    parent_id       BIGINT,
    category_level  TINYINT      NOT NULL COMMENT '1-一级 2-二级',
    description     VARCHAR(255),
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_code (category_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_category_name (category_name),
    CONSTRAINT fk_dim_job_category_parent
        FOREIGN KEY (parent_id) REFERENCES dim_job_category(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位类别字典表';

-- ============================================================
-- 4. 用户筛选条件/偏好表
-- ============================================================
CREATE TABLE IF NOT EXISTS user_report_preference (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    preference_name     VARCHAR(100) NOT NULL,
    major_id            BIGINT,
    region_code         VARCHAR(20),
    province_code       VARCHAR(20),
    city_code           VARCHAR(20),
    salary_min          INT,
    salary_max          INT,
    job_category_id     BIGINT,
    company_name_keyword VARCHAR(100),
    company_industry    VARCHAR(100),
    company_size        VARCHAR(50),
    company_finance     VARCHAR(50),
    sort_type           VARCHAR(50),
    is_default          TINYINT      NOT NULL DEFAULT 0 COMMENT '0-否 1-是',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pref_user (user_id),
    INDEX idx_pref_major (major_id),
    INDEX idx_pref_category (job_category_id),
    CONSTRAINT fk_user_report_preference_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_report_preference_major
        FOREIGN KEY (major_id) REFERENCES dim_major(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_report_preference_region
        FOREIGN KEY (region_code) REFERENCES dim_region(region_code) ON DELETE SET NULL,
    CONSTRAINT fk_user_report_preference_province
        FOREIGN KEY (province_code) REFERENCES dim_region(region_code) ON DELETE SET NULL,
    CONSTRAINT fk_user_report_preference_city
        FOREIGN KEY (city_code) REFERENCES dim_region(region_code) ON DELETE SET NULL,
    CONSTRAINT fk_user_report_preference_job_category
        FOREIGN KEY (job_category_id) REFERENCES dim_job_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户筛选条件/偏好表';

-- ============================================================
-- 5. 报告筛选条件与数据快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_report_snapshot (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           BIGINT   NOT NULL,
    task_id             BIGINT,
    user_id             BIGINT,
    filter_snapshot     JSON     NOT NULL,
    data_snapshot       JSON,
    data_start_time     DATETIME,
    data_end_time       DATETIME,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report_snapshot_report (report_id),
    INDEX idx_report_snapshot_task (task_id),
    INDEX idx_report_snapshot_user (user_id),
    CONSTRAINT fk_report_snapshot_report
        FOREIGN KEY (report_id) REFERENCES biz_analysis_report(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_snapshot_task
        FOREIGN KEY (task_id) REFERENCES biz_analysis_task(id) ON DELETE SET NULL,
    CONSTRAINT fk_report_snapshot_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告筛选条件与数据快照表';

-- ============================================================
-- 6. 职位标签字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS job_label_dict (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    label_name          VARCHAR(100) NOT NULL,
    label_code          VARCHAR(64),
    label_type          VARCHAR(50)  NOT NULL COMMENT 'skill/industry/tool/language/framework/other',
    parent_id           BIGINT,
    alias_name          VARCHAR(255),
    description         VARCHAR(255),
    sort_no             INT DEFAULT 0,
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_label_name_type (label_name, label_type),
    UNIQUE KEY uk_label_code (label_code),
    INDEX idx_label_parent (parent_id),
    CONSTRAINT fk_job_label_dict_parent
        FOREIGN KEY (parent_id) REFERENCES job_label_dict(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位标签字典表';

-- ============================================================
-- 7. 职位标签关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS job_label_rel (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_posting_id      BIGINT       NOT NULL,
    label_id            BIGINT       NOT NULL,
    source_type         VARCHAR(32) COMMENT 'crawl_extract/manual/ai_extract',
    confidence_score    DECIMAL(5,2),
    is_core             TINYINT      NOT NULL DEFAULT 0 COMMENT '0-否 1-是',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_label_rel (job_posting_id, label_id),
    INDEX idx_job_label_rel_label (label_id),
    CONSTRAINT fk_job_label_rel_job
        FOREIGN KEY (job_posting_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_label_rel_label
        FOREIGN KEY (label_id) REFERENCES job_label_dict(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位标签关联表';

-- ============================================================
-- 8. 福利字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS job_welfare_dict (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    welfare_name        VARCHAR(100) NOT NULL,
    welfare_code        VARCHAR(64),
    welfare_type        VARCHAR(50) COMMENT 'insurance/holiday/subsidy/travel/bonus/other',
    parent_id           BIGINT,
    description         VARCHAR(255),
    sort_no             INT DEFAULT 0,
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_welfare_name (welfare_name),
    UNIQUE KEY uk_welfare_code (welfare_code),
    INDEX idx_welfare_parent (parent_id),
    CONSTRAINT fk_job_welfare_dict_parent
        FOREIGN KEY (parent_id) REFERENCES job_welfare_dict(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='福利字典表';

-- ============================================================
-- 9. 职位福利关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS job_welfare_rel (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_posting_id      BIGINT      NOT NULL,
    welfare_id          BIGINT      NOT NULL,
    source_type         VARCHAR(32) COMMENT 'crawl_extract/manual/ai_extract',
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_welfare_rel (job_posting_id, welfare_id),
    INDEX idx_job_welfare_rel_welfare (welfare_id),
    CONSTRAINT fk_job_welfare_rel_job
        FOREIGN KEY (job_posting_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_welfare_rel_welfare
        FOREIGN KEY (welfare_id) REFERENCES job_welfare_dict(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位福利关联表';

-- ============================================================
-- 10. 专业-岗位匹配规则表
-- ============================================================
CREATE TABLE IF NOT EXISTS major_job_match_rule (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    major_id            BIGINT         NOT NULL,
    job_category_id     BIGINT         NOT NULL,
    match_weight        DECIMAL(5,2)   NOT NULL,
    core_skills         VARCHAR(255),
    rule_source         VARCHAR(50) COMMENT 'manual/ai/statistical',
    status              TINYINT        NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    remark              VARCHAR(255),
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_major_job_rule (major_id, job_category_id),
    CONSTRAINT fk_major_job_rule_major
        FOREIGN KEY (major_id) REFERENCES dim_major(id) ON DELETE CASCADE,
    CONSTRAINT fk_major_job_rule_category
        FOREIGN KEY (job_category_id) REFERENCES dim_job_category(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业-岗位匹配规则表';

-- ============================================================
-- 11. 必须补齐的现有表字段
-- ============================================================

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN company_finance VARCHAR(50) COMMENT ''未融资/A轮/上市公司等'' AFTER company_type',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'company_finance'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN region_code VARCHAR(20) COMMENT ''关联 dim_region.region_code'' AFTER company_finance',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN province_code VARCHAR(20) COMMENT ''关联 dim_region.region_code'' AFTER region_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN city_code VARCHAR(20) COMMENT ''关联 dim_region.region_code'' AFTER province_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'updated_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN region_code VARCHAR(20) COMMENT ''关联 dim_region.region_code'' AFTER company_name',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN province_code VARCHAR(20) COMMENT ''关联 dim_region.region_code'' AFTER region_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN city_code VARCHAR(20) COMMENT ''关联 dim_region.region_code'' AFTER province_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN job_category_id BIGINT COMMENT ''关联 dim_job_category.id'' AFTER job_classification',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'job_category_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 12. 必须补齐的索引
-- ============================================================

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_company_region_code ON biz_company(region_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND INDEX_NAME = 'idx_biz_company_region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_company_province_code ON biz_company(province_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND INDEX_NAME = 'idx_biz_company_province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_company_city_code ON biz_company(city_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND INDEX_NAME = 'idx_biz_company_city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_job_posting_region_code ON biz_job_posting(region_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND INDEX_NAME = 'idx_biz_job_posting_region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_job_posting_province_code ON biz_job_posting(province_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND INDEX_NAME = 'idx_biz_job_posting_province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_job_posting_city_code ON biz_job_posting(city_code)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND INDEX_NAME = 'idx_biz_job_posting_city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_biz_job_posting_category_id ON biz_job_posting(job_category_id)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND INDEX_NAME = 'idx_biz_job_posting_category_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 13. 必须补齐的外键
-- ============================================================

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD CONSTRAINT fk_biz_company_region_code FOREIGN KEY (region_code) REFERENCES dim_region(region_code) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND CONSTRAINT_NAME = 'fk_biz_company_region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD CONSTRAINT fk_biz_company_province_code FOREIGN KEY (province_code) REFERENCES dim_region(region_code) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND CONSTRAINT_NAME = 'fk_biz_company_province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD CONSTRAINT fk_biz_company_city_code FOREIGN KEY (city_code) REFERENCES dim_region(region_code) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND CONSTRAINT_NAME = 'fk_biz_company_city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD CONSTRAINT fk_biz_job_posting_region_code FOREIGN KEY (region_code) REFERENCES dim_region(region_code) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND CONSTRAINT_NAME = 'fk_biz_job_posting_region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD CONSTRAINT fk_biz_job_posting_province_code FOREIGN KEY (province_code) REFERENCES dim_region(region_code) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND CONSTRAINT_NAME = 'fk_biz_job_posting_province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD CONSTRAINT fk_biz_job_posting_city_code FOREIGN KEY (city_code) REFERENCES dim_region(region_code) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND CONSTRAINT_NAME = 'fk_biz_job_posting_city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD CONSTRAINT fk_biz_job_posting_job_category FOREIGN KEY (job_category_id) REFERENCES dim_job_category(id) ON DELETE SET NULL',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND CONSTRAINT_NAME = 'fk_biz_job_posting_job_category'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
