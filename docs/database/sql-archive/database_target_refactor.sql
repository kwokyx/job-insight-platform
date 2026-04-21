USE career_platform;

CREATE TABLE IF NOT EXISTS dim_industry (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    industry_code   VARCHAR(50)  NOT NULL,
    industry_name   VARCHAR(100) NOT NULL,
    parent_id       BIGINT,
    industry_level  TINYINT      NOT NULL COMMENT '1-level 2-level',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1-enabled 0-disabled',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dim_industry_code (industry_code),
    INDEX idx_dim_industry_parent (parent_id),
    CONSTRAINT fk_dim_industry_parent
        FOREIGN KEY (parent_id) REFERENCES dim_industry(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Industry dimension table';

CREATE TABLE IF NOT EXISTS user_profile (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id                 BIGINT       NOT NULL,
    major_id                BIGINT,
    education_level         VARCHAR(50),
    target_region_code      VARCHAR(20),
    target_province_code    VARCHAR(20),
    target_city_code        VARCHAR(20),
    expected_salary_min     INT,
    expected_salary_max     INT,
    target_job_category_id  BIGINT,
    skills                  TEXT,
    profile_summary         VARCHAR(255),
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_profile_user (user_id),
    CONSTRAINT fk_user_profile_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profile_major
        FOREIGN KEY (major_id) REFERENCES dim_major(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_profile_region
        FOREIGN KEY (target_region_code) REFERENCES dim_region(region_code) ON DELETE SET NULL,
    CONSTRAINT fk_user_profile_province
        FOREIGN KEY (target_province_code) REFERENCES dim_region(region_code) ON DELETE SET NULL,
    CONSTRAINT fk_user_profile_city
        FOREIGN KEY (target_city_code) REFERENCES dim_region(region_code) ON DELETE SET NULL,
    CONSTRAINT fk_user_profile_job_category
        FOREIGN KEY (target_job_category_id) REFERENCES dim_job_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User profile table';

CREATE TABLE IF NOT EXISTS biz_recommendation_result (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    report_id       BIGINT,
    preference_id   BIGINT,
    job_posting_id  BIGINT       NOT NULL,
    match_score     DECIMAL(5,2) NOT NULL,
    match_reason    VARCHAR(500),
    rank_no         INT,
    is_viewed       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-unviewed 1-viewed',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recommendation_user (user_id),
    INDEX idx_recommendation_report (report_id),
    INDEX idx_recommendation_preference (preference_id),
    INDEX idx_recommendation_job (job_posting_id),
    CONSTRAINT fk_recommendation_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_report
        FOREIGN KEY (report_id) REFERENCES biz_analysis_report(id) ON DELETE SET NULL,
    CONSTRAINT fk_recommendation_preference
        FOREIGN KEY (preference_id) REFERENCES user_report_preference(id) ON DELETE SET NULL,
    CONSTRAINT fk_recommendation_job
        FOREIGN KEY (job_posting_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recommendation result table';

CREATE TABLE IF NOT EXISTS agent_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT,
    user_id         BIGINT       NOT NULL,
    task_type       VARCHAR(50)  NOT NULL,
    task_title      VARCHAR(200) NOT NULL,
    task_goal       TEXT,
    input_payload   JSON,
    status          VARCHAR(20)  NOT NULL COMMENT 'pending/running/success/failed/cancelled',
    priority        INT DEFAULT 5,
    started_at      DATETIME,
    finished_at     DATETIME,
    error_message   VARCHAR(500),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_task_user (user_id),
    INDEX idx_agent_task_conversation (conversation_id),
    INDEX idx_agent_task_status (status),
    CONSTRAINT fk_agent_task_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE SET NULL,
    CONSTRAINT fk_agent_task_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent task table';

CREATE TABLE IF NOT EXISTS agent_task_step (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT       NOT NULL,
    step_no         INT          NOT NULL,
    step_name       VARCHAR(200) NOT NULL,
    step_type       VARCHAR(50)  NOT NULL COMMENT 'plan/search/query/tool/summary/output',
    input_data      JSON,
    output_data     JSON,
    status          VARCHAR(20)  NOT NULL COMMENT 'pending/running/success/failed/skipped',
    error_message   VARCHAR(500),
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_task_step (task_id, step_no),
    CONSTRAINT fk_agent_task_step_task
        FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent task step table';

CREATE TABLE IF NOT EXISTS agent_tool_call (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id           BIGINT       NOT NULL,
    step_id           BIGINT,
    tool_name         VARCHAR(100) NOT NULL,
    tool_type         VARCHAR(50),
    request_payload   JSON,
    response_payload  JSON,
    status            VARCHAR(20)  NOT NULL COMMENT 'success/failed/timeout',
    latency_ms        INT,
    error_message     VARCHAR(500),
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_tool_call_task (task_id),
    INDEX idx_agent_tool_call_step (step_id),
    CONSTRAINT fk_agent_tool_call_task
        FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_call_step
        FOREIGN KEY (step_id) REFERENCES agent_task_step(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent tool call table';

CREATE TABLE IF NOT EXISTS ai_context_memory (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT,
    conversation_id   BIGINT,
    memory_type       VARCHAR(50)  NOT NULL COMMENT 'profile/preference/summary/task_context',
    memory_key        VARCHAR(100) NOT NULL,
    memory_value      TEXT         NOT NULL,
    source_type       VARCHAR(50),
    importance_score  DECIMAL(5,2),
    expired_at        DATETIME,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ai_memory_user (user_id),
    INDEX idx_ai_memory_conversation (conversation_id),
    INDEX idx_ai_memory_type_key (memory_type, memory_key),
    CONSTRAINT fk_ai_memory_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_memory_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI context memory table';

CREATE TABLE IF NOT EXISTS ai_artifact (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id           BIGINT,
    conversation_id   BIGINT,
    artifact_type     VARCHAR(50)  NOT NULL COMMENT 'report/json/markdown/sql/chart/file',
    artifact_name     VARCHAR(200) NOT NULL,
    content_text      LONGTEXT,
    content_json      JSON,
    file_url          VARCHAR(500),
    version_no        INT DEFAULT 1,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_artifact_task (task_id),
    INDEX idx_ai_artifact_conversation (conversation_id),
    CONSTRAINT fk_ai_artifact_task
        FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_artifact_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI artifact table';

CREATE TABLE IF NOT EXISTS ai_feedback (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id   BIGINT,
    message_id        BIGINT,
    task_id           BIGINT,
    user_id           BIGINT       NOT NULL,
    feedback_type     VARCHAR(50)  NOT NULL COMMENT 'like/dislike/rating/correction',
    rating_score      INT,
    feedback_text     VARCHAR(500),
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_feedback_user (user_id),
    INDEX idx_ai_feedback_conversation (conversation_id),
    INDEX idx_ai_feedback_message (message_id),
    INDEX idx_ai_feedback_task (task_id),
    CONSTRAINT fk_ai_feedback_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_feedback_message
        FOREIGN KEY (message_id) REFERENCES ai_message(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_feedback_task
        FOREIGN KEY (task_id) REFERENCES agent_task(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_feedback_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI feedback table';

CREATE TABLE IF NOT EXISTS crawl_task (
    task_id            VARCHAR(64) PRIMARY KEY,
    parent_task_id     VARCHAR(64),
    task_name          VARCHAR(100) NOT NULL,
    channel            VARCHAR(32)  NOT NULL,
    keywords           TEXT,
    city               VARCHAR(100),
    status             TINYINT      NOT NULL DEFAULT 0 COMMENT '0-pending 1-running 2-finished 3-failed',
    priority           INT          NOT NULL DEFAULT 5,
    total_count        INT DEFAULT 0,
    finished_count     INT DEFAULT 0,
    duplicate_count    INT DEFAULT 0,
    start_time         DATETIME,
    end_time           DATETIME,
    create_user        VARCHAR(50),
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crawl_task_parent (parent_task_id),
    INDEX idx_crawl_task_channel (channel),
    INDEX idx_crawl_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawl task table';

CREATE TABLE IF NOT EXISTS crawl_worker (
    worker_id        VARCHAR(64) PRIMARY KEY,
    ip               VARCHAR(50) NOT NULL,
    status           TINYINT     NOT NULL DEFAULT 0 COMMENT '0-offline 1-online',
    current_task_id  VARCHAR(64),
    cpu_usage        FLOAT DEFAULT 0,
    memory_usage     FLOAT DEFAULT 0,
    last_heartbeat   DATETIME    NOT NULL,
    INDEX idx_crawl_worker_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawl worker table';

CREATE TABLE IF NOT EXISTS crawl_task_shard (
    shard_id         VARCHAR(64) PRIMARY KEY,
    task_id          VARCHAR(64)  NOT NULL,
    page             INT          NOT NULL,
    keyword          VARCHAR(100),
    city             VARCHAR(100),
    category_code    VARCHAR(50),
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0-pending 1-running 2-finished 3-failed',
    retry_count      INT          NOT NULL DEFAULT 0,
    worker_id        VARCHAR(64),
    start_time       DATETIME,
    end_time         DATETIME,
    INDEX idx_shard_task (task_id),
    INDEX idx_shard_worker (worker_id),
    CONSTRAINT fk_crawl_task_shard_task
        FOREIGN KEY (task_id) REFERENCES crawl_task(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_crawl_task_shard_worker
        FOREIGN KEY (worker_id) REFERENCES crawl_worker(worker_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawl task shard table';

CREATE TABLE IF NOT EXISTS crawl_proxy (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    proxy_ip           VARCHAR(50) NOT NULL,
    protocol           VARCHAR(10) NOT NULL DEFAULT 'http',
    status             TINYINT     NOT NULL DEFAULT 1 COMMENT '0-unavailable 1-available',
    fail_count         INT DEFAULT 0,
    success_rate       FLOAT DEFAULT 100,
    avg_response_time  FLOAT DEFAULT 0,
    last_used_time     DATETIME,
    worker_id          VARCHAR(64),
    create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_crawl_proxy_worker (worker_id),
    CONSTRAINT fk_crawl_proxy_worker
        FOREIGN KEY (worker_id) REFERENCES crawl_worker(worker_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawl proxy table';

CREATE TABLE IF NOT EXISTS system_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key      VARCHAR(100) NOT NULL,
    config_value    TEXT         NOT NULL,
    channel         VARCHAR(32)  NOT NULL DEFAULT 'default',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0-disabled 1-enabled',
    expire_time     DATETIME,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_system_config_key_channel (config_key, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System config table';

CREATE TABLE IF NOT EXISTS crawl_task_log (
    log_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         VARCHAR(64)  NOT NULL,
    shard_id        VARCHAR(64),
    worker_id       VARCHAR(64),
    level           VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    message         TEXT         NOT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_crawl_task_log_task (task_id),
    INDEX idx_crawl_task_log_shard (shard_id),
    INDEX idx_crawl_task_log_worker (worker_id),
    CONSTRAINT fk_crawl_task_log_task
        FOREIGN KEY (task_id) REFERENCES crawl_task(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_crawl_task_log_shard
        FOREIGN KEY (shard_id) REFERENCES crawl_task_shard(shard_id) ON DELETE SET NULL,
    CONSTRAINT fk_crawl_task_log_worker
        FOREIGN KEY (worker_id) REFERENCES crawl_worker(worker_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Crawl task log table';

CREATE TABLE IF NOT EXISTS crawl_job_posting (
    url                 VARCHAR(500),
    url_obj_id          VARCHAR(100) PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    salary_min          INT DEFAULT 0,
    salary_max          INT DEFAULT 0,
    salary_raw          VARCHAR(50),
    job_city            VARCHAR(50)  NOT NULL,
    experience_year     VARCHAR(50),
    education_need      VARCHAR(50),
    publish_date        DATETIME,
    job_welfare         TEXT,
    job_labels          TEXT,
    position_info       TEXT,
    job_classification  VARCHAR(100),
    company_name        VARCHAR(100),
    company_size        VARCHAR(50),
    company_finance     VARCHAR(50),
    crawl_time          DATETIME     NOT NULL,
    crawl_update_time   DATETIME,
    company_logo        VARCHAR(500),
    task_id             VARCHAR(64),
    INDEX idx_crawl_job_task (task_id),
    INDEX idx_crawl_job_city (job_city),
    CONSTRAINT fk_crawl_job_task
        FOREIGN KEY (task_id) REFERENCES crawl_task(task_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Distributed crawl job table';

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN company_finance VARCHAR(50) NULL AFTER company_type',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'company_finance'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN region_code VARCHAR(20) NULL AFTER company_finance',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN province_code VARCHAR(20) NULL AFTER region_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_company ADD COLUMN city_code VARCHAR(20) NULL AFTER province_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_company' AND COLUMN_NAME = 'city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN region_code VARCHAR(20) NULL AFTER company_name',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'region_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN province_code VARCHAR(20) NULL AFTER region_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'province_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN city_code VARCHAR(20) NULL AFTER province_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'city_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE biz_job_posting ADD COLUMN job_category_id BIGINT NULL AFTER industry_name',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'job_category_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
