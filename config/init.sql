-- ============================================================
-- 职业能力大数据服务平台 — 完整数据库初始化脚本
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS career_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE career_platform;

-- ============================================================
-- 1. 用户表（三级角色：0-普通用户/学生 1-管理员 2-教师）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录账号',
    nickname        VARCHAR(100) COMMENT '显示昵称',
    email           VARCHAR(100) UNIQUE,
    phone           VARCHAR(20)  UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    role_type       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-普通用户/学生 1-管理员 2-教师',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常 2-锁定',
    last_login_at   DATETIME,
    last_login_ip   VARCHAR(50),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role_type (role_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 2. 公司/企业表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_company (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_name    VARCHAR(255) NOT NULL,
    short_name      VARCHAR(100),
    industry        VARCHAR(100),
    company_size    VARCHAR(50)  COMMENT '规模: 0-20人/20-99人/100-499人/500+',
    company_type    VARCHAR(50)  COMMENT '性质: 民企/国企/外企/合资',
    company_finance VARCHAR(50),
    region_code     VARCHAR(20),
    province_code   VARCHAR(20),
    city_code       VARCHAR(20),
    address         VARCHAR(500),
    website         VARCHAR(500),
    logo_url        VARCHAR(500),
    description     TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_industry (industry),
    INDEX idx_name (company_name),
    INDEX idx_region_code (region_code),
    INDEX idx_province_code (province_code),
    INDEX idx_city_code (city_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业信息表';

-- ============================================================
-- 3. 行业分类表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_industry (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    industry_code   VARCHAR(50) NOT NULL UNIQUE,
    industry_name   VARCHAR(100) NOT NULL,
    parent_id       BIGINT DEFAULT 0 COMMENT '上级行业（树形结构）',
    level           TINYINT DEFAULT 1 COMMENT '层级: 1-大类 2-中类 3-小类',
    sort_order      INT DEFAULT 0,
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业分类表';

-- ============================================================
-- 4. 职位数据表（核心大表）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_job_posting (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id_source   VARCHAR(100) COMMENT '来源站点的原始ID（去重用）',
    title           VARCHAR(255) NOT NULL,
    company_id      BIGINT COMMENT '关联企业',
    company_name    VARCHAR(255) NOT NULL COMMENT '冗余：企业名（查询性能）',
    region          VARCHAR(100) COMMENT '省份区域',
    city            VARCHAR(100) COMMENT '城市',
    district        VARCHAR(100) COMMENT '区县',
    industry_id     BIGINT COMMENT '关联行业',
    industry_name   VARCHAR(100) COMMENT '冗余：行业名',
    education       VARCHAR(50)  COMMENT '学历要求',
    experience      VARCHAR(50)  COMMENT '经验要求',
    salary_min      DECIMAL(10,2) COMMENT '最低薪资(千元/月)',
    salary_max      DECIMAL(10,2) COMMENT '最高薪资(千元/月)',
    salary_unit     VARCHAR(20) DEFAULT 'monthly' COMMENT '薪资单位: monthly/yearly/daily',
    salary_text     VARCHAR(100) COMMENT '原始薪资文本',
    employment_type VARCHAR(50) DEFAULT '全职' COMMENT '全职/兼职/实习',
    job_benefits    JSON COMMENT '福利标签',
    description     TEXT COMMENT '职位描述（全文）',
    requirements    TEXT COMMENT '任职要求',
    source_site     VARCHAR(100) NOT NULL COMMENT '来源: 51job/智联/BOSS直聘',
    source_url      VARCHAR(500),
    publish_date    DATE,
    crawl_time      DATETIME NOT NULL,
    data_quality    TINYINT DEFAULT 3 COMMENT '数据质量评分 1-5',
    is_active       TINYINT DEFAULT 1 COMMENT '是否有效',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_source_job (source_site, job_id_source),
    INDEX idx_title (title),
    INDEX idx_city (city),
    INDEX idx_industry (industry_id),
    INDEX idx_education (education),
    INDEX idx_salary (salary_min, salary_max),
    INDEX idx_publish_date (publish_date),
    INDEX idx_crawl_time (crawl_time),
    FULLTEXT INDEX ft_description (description, requirements)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位数据表';

-- ============================================================
-- 5. 技能标签表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_skill (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_name  VARCHAR(100) NOT NULL UNIQUE,
    category    VARCHAR(50) COMMENT '分类: 编程语言/框架/工具/软技能',
    hot_score   INT DEFAULT 0 COMMENT '热度评分（定时更新）',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_hot (hot_score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能标签表';

-- ============================================================
-- 6. 职位-技能关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_job_skill (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id      BIGINT NOT NULL,
    skill_id    BIGINT NOT NULL,
    weight      DECIMAL(3,2) DEFAULT 1.00 COMMENT '技能权重',
    UNIQUE KEY uk_job_skill (job_id, skill_id),
    INDEX idx_skill (skill_id),
    FOREIGN KEY (job_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES biz_skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位技能关联表';

-- ============================================================
-- 7. 宏观就业指标表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_employment_indicator (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    indicator_name  VARCHAR(255) NOT NULL COMMENT '指标名称',
    indicator_code  VARCHAR(100) COMMENT '指标编码',
    metric_scope    VARCHAR(100) COMMENT '范围: 全国/省级/行业',
    period          VARCHAR(50)  COMMENT '时间周期: 2026-Q1',
    region          VARCHAR(100),
    value           DECIMAL(12,4),
    unit            VARCHAR(50) COMMENT '单位: %/万人/亿元',
    source_site     VARCHAR(100),
    source_url      VARCHAR(500),
    publish_date    DATE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_indicator (indicator_code),
    INDEX idx_period (period),
    INDEX idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宏观就业指标表';

-- ============================================================
-- 8. 用户画像表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_user_profile (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL UNIQUE,
    real_name       VARCHAR(50),
    gender          TINYINT COMMENT '0-女 1-男 2-未知',
    university      VARCHAR(100),
    major           VARCHAR(100),
    education       VARCHAR(50) COMMENT '学历: 大专/本科/硕士/博士',
    graduation_year INT COMMENT '毕业年份',
    preferred_cities JSON COMMENT '意向城市',
    preferred_industries JSON COMMENT '意向行业',
    career_goal     TEXT COMMENT '职业目标描述',
    resume_url      VARCHAR(500) COMMENT '简历文件URL',
    resume_parsed   JSON COMMENT '简历解析结果（结构化）',
    skill_vector    JSON COMMENT '技能向量（用于推荐算法）',
    competency_score DECIMAL(5,2) COMMENT '综合能力评分',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_major (major),
    INDEX idx_education (education)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

-- ============================================================
-- 9. 用户-技能关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_user_skill (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id      BIGINT NOT NULL,
    skill_id        BIGINT NOT NULL,
    proficiency     TINYINT DEFAULT 3 COMMENT '熟练度 1-5',
    source          VARCHAR(50) COMMENT '来源: 自填/简历解析/课程成绩',
    UNIQUE KEY uk_profile_skill (profile_id, skill_id),
    FOREIGN KEY (profile_id) REFERENCES biz_user_profile(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES biz_skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户技能关联表';

-- ============================================================
-- 10. AI 会话表
-- ============================================================
-- ============================================================
-- 10. 推荐任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_recommend_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '发起推荐的用户',
    scene_type      VARCHAR(50) NOT NULL COMMENT 'JOB/SKILL_GAP/CAREER_PATH/SIMILAR_JOB',
    trigger_type    VARCHAR(30) DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULED/EVENT',
    request_params  JSON COMMENT '推荐请求参数',
    profile_snapshot JSON COMMENT '用户画像快照',
    algorithm_source VARCHAR(30) DEFAULT 'LOCAL' COMMENT 'LOCAL/ALGORITHM_SERVICE',
    algorithm_version VARCHAR(50) COMMENT '算法版本',
    status          VARCHAR(20) DEFAULT 'SUCCESS' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    total_results   INT DEFAULT 0 COMMENT '返回结果数',
    latency_ms      INT COMMENT '推荐耗时(ms)',
    error_message   TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_task_user (user_id),
    INDEX idx_task_scene (scene_type),
    INDEX idx_task_status (status),
    INDEX idx_task_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐任务表';

-- ============================================================
-- 11. 推荐结果表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_recommend_result (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT NOT NULL COMMENT '关联推荐任务',
    user_id         BIGINT NOT NULL COMMENT '被推荐的用户',
    job_id          BIGINT COMMENT '被推荐岗位',
    result_type     VARCHAR(30) DEFAULT 'JOB' COMMENT 'JOB/SKILL/CAREER_PATH',
    rank_no         INT NOT NULL COMMENT '推荐排序位次',
    score           DECIMAL(8,4) COMMENT '匹配分数',
    score_detail    JSON COMMENT '分数组成明细',
    matched_skills  JSON COMMENT '已匹配技能',
    missing_skills  JSON COMMENT '技能缺口',
    result_payload  JSON COMMENT '结果快照',
    exposed_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次曝光时间',
    clicked_at      DATETIME COMMENT '点击时间',
    is_clicked      TINYINT DEFAULT 0,
    is_saved        TINYINT DEFAULT 0 COMMENT '是否收藏',
    is_applied      TINYINT DEFAULT 0 COMMENT '是否投递',
    FOREIGN KEY (task_id) REFERENCES biz_recommend_task(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES biz_job_posting(id) ON DELETE SET NULL,
    UNIQUE KEY uk_task_rank (task_id, rank_no),
    UNIQUE KEY uk_task_job (task_id, job_id),
    INDEX idx_result_user (user_id),
    INDEX idx_result_job (job_id),
    INDEX idx_result_type (result_type),
    INDEX idx_result_score (score),
    INDEX idx_result_exposed (exposed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐结果表';

-- ============================================================
-- 12. 用户岗位行为表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_user_job_action (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    job_id              BIGINT NOT NULL,
    recommend_result_id BIGINT COMMENT '如来自推荐则关联推荐结果',
    action_type         VARCHAR(30) NOT NULL COMMENT 'EXPOSE/CLICK/VIEW/SAVE/UNSAVE/APPLY/SHARE/DISMISS',
    action_source       VARCHAR(30) DEFAULT 'DIRECT' COMMENT 'RECOMMEND/SEARCH/DIRECT/SUBSCRIPTION',
    action_value        DECIMAL(8,2) COMMENT '行为权重或停留时长',
    metadata            JSON COMMENT '附加信息',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    FOREIGN KEY (recommend_result_id) REFERENCES biz_recommend_result(id) ON DELETE SET NULL,
    INDEX idx_action_user (user_id),
    INDEX idx_action_job (job_id),
    INDEX idx_action_type (action_type),
    INDEX idx_action_created (created_at),
    INDEX idx_action_user_type (user_id, action_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户岗位行为表';

CREATE TABLE IF NOT EXISTS ai_conversation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    session_id      VARCHAR(64) NOT NULL UNIQUE COMMENT '会话唯一标识',
    title           VARCHAR(200) COMMENT '会话标题',
    context_type    VARCHAR(50) COMMENT '上下文类型: general/job_analysis/career_advice',
    status          TINYINT DEFAULT 1 COMMENT '1-活跃 0-已归档',
    message_count   INT DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

-- ============================================================
-- 11. AI 消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL COMMENT 'user/assistant/system',
    content         TEXT NOT NULL,
    content_type    VARCHAR(20) DEFAULT 'text' COMMENT 'text/chart/table/markdown',
    metadata        JSON COMMENT '附加数据',
    tokens_used     INT COMMENT 'Token消耗量',
    latency_ms      INT COMMENT '响应耗时',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE CASCADE,
    INDEX idx_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息记录表';

-- ============================================================
-- 12. 分析任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_analysis_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_name       VARCHAR(200) NOT NULL,
    task_type       VARCHAR(50) NOT NULL COMMENT 'SALARY/SKILL/TREND/INDUSTRY/COMPREHENSIVE',
    params          JSON COMMENT '任务参数',
    status          VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    progress        TINYINT DEFAULT 0 COMMENT '进度百分比 0-100',
    result_summary  JSON COMMENT '分析结果摘要',
    result_file_url VARCHAR(500) COMMENT '完整结果文件',
    error_message   TEXT,
    created_by      BIGINT COMMENT '创建人',
    started_at      DATETIME,
    completed_at    DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (task_type),
    INDEX idx_status (status),
    INDEX idx_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析任务表';

-- ============================================================
-- 13. 分析报告表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_analysis_report (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT COMMENT '关联任务',
    report_name     VARCHAR(200) NOT NULL,
    report_type     VARCHAR(50) NOT NULL COMMENT 'INDUSTRY/SALARY/SKILL/COMPREHENSIVE/CUSTOM',
    report_format   VARCHAR(20) DEFAULT 'HTML' COMMENT 'HTML/PDF/EXCEL',
    description     TEXT,
    analysis_data   JSON COMMENT '结构化分析数据',
    file_url        VARCHAR(500) COMMENT '报告文件OSS地址',
    file_size       BIGINT COMMENT '文件大小(bytes)',
    is_public       TINYINT DEFAULT 0 COMMENT '是否公开',
    view_count      INT DEFAULT 0,
    download_count  INT DEFAULT 0,
    generated_by    BIGINT COMMENT '生成人',
    generated_at    DATETIME NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (report_type),
    INDEX idx_public (is_public),
    INDEX idx_generated (generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析报告表';

-- ============================================================
-- 14. 操作日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT,
    username        VARCHAR(50),
    operation       VARCHAR(200) COMMENT '操作描述',
    method          VARCHAR(200) COMMENT '请求方法',
    request_url     VARCHAR(500),
    request_params  TEXT,
    response_code   INT,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    duration_ms     INT COMMENT '耗时(毫秒)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================
-- 15. 用户订阅/推送配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_user_subscription (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    subscription_type VARCHAR(50) NOT NULL COMMENT 'JOB_PUSH/REPORT_NOTIFY/TREND_ALERT',
    filter_config   JSON COMMENT '推送过滤条件',
    channel         VARCHAR(20) DEFAULT 'IN_APP' COMMENT 'EMAIL/IN_APP',
    is_active       TINYINT DEFAULT 1,
    last_pushed_at  DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_type (subscription_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅配置表';

-- ============================================================
-- 16. 数据采集任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_crawl_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_name       VARCHAR(200) NOT NULL,
    source_site     VARCHAR(100) NOT NULL COMMENT '数据源: 51job/zhaopin/boss',
    task_config     JSON COMMENT '采集配置',
    status          VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    total_count     INT DEFAULT 0 COMMENT '采集总条数',
    success_count   INT DEFAULT 0,
    fail_count      INT DEFAULT 0,
    duplicate_count INT DEFAULT 0 COMMENT '去重条数',
    started_at      DATETIME,
    completed_at    DATETIME,
    created_by      BIGINT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source (source_site),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据采集任务表';

-- ============================================================
-- 初始数据：管理员账号
-- 密码: admin123 (BCrypt: $2a$10$...)
-- ============================================================
INSERT IGNORE INTO sys_user (username, nickname, email, password_hash, role_type, status)
VALUES ('admin', '系统管理员', 'admin@career-platform.edu.cn',
        '$2a$10$siXMDw.9nEnYGr98Pm2/O.FWzTgJvZ3TRa..s9NES1s.NsBEwzOz.',
        1, 1);

-- ============================================================
-- 17. 采集日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_crawl_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT NOT NULL COMMENT '关联采集任务',
    proxy_ip        VARCHAR(50) COMMENT '使用的代理IP',
    target_url      VARCHAR(500) COMMENT '采集目标URL',
    http_status     INT COMMENT 'HTTP响应码',
    response_time   INT COMMENT '响应耗时(ms)',
    records_found   INT DEFAULT 0 COMMENT '本次发现记录数',
    error_message   TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES biz_crawl_task(id) ON DELETE CASCADE,
    INDEX idx_task (task_id),
    INDEX idx_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集日志明细表';

-- ============================================================
-- 18. 数据源配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_data_source (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_name     VARCHAR(100) NOT NULL COMMENT '数据源名称: 前程无忧/智联/BOSS直聘',
    source_code     VARCHAR(50) NOT NULL UNIQUE COMMENT '编码: 51job/zhaopin/boss',
    base_url        VARCHAR(500) COMMENT 'API/站点基础URL',
    crawl_strategy  JSON COMMENT '采集策略（频率、并发数、重试次数）',
    is_active       TINYINT DEFAULT 1 COMMENT '是否启用',
    last_crawl_at   DATETIME COMMENT '最近采集时间',
    total_records   BIGINT DEFAULT 0 COMMENT '累计采集数据量',
    health_status   VARCHAR(20) DEFAULT 'UNKNOWN' COMMENT 'HEALTHY/DEGRADED/DOWN/UNKNOWN',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- ============================================================
-- 19. 岗位历史版本追踪表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_job_history (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id          BIGINT NOT NULL COMMENT '关联职位',
    snapshot_data   JSON NOT NULL COMMENT '快照数据(薪资、状态、描述等)',
    salary_min      DECIMAL(10,2),
    salary_max      DECIMAL(10,2),
    is_active       TINYINT COMMENT '当时是否有效',
    change_type     VARCHAR(50) COMMENT 'SALARY_CHANGE/STATUS_CHANGE/DESC_CHANGE/NEW',
    crawl_time      DATETIME NOT NULL COMMENT '采集时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    INDEX idx_job (job_id),
    INDEX idx_time (crawl_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位历史版本追踪表';

-- ============================================================
-- 20. 数仓 — 明细宽表 (DWD)
-- ============================================================
CREATE TABLE IF NOT EXISTS dwd_job_fact (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_id          BIGINT NOT NULL COMMENT '原始职位ID',
    title           VARCHAR(255) NOT NULL,
    company_name    VARCHAR(255),
    city_std        VARCHAR(50) COMMENT '标准化城市名',
    province        VARCHAR(50) COMMENT '所属省份',
    industry_std    VARCHAR(100) COMMENT '标准化行业名',
    industry_l1     VARCHAR(100) COMMENT '一级行业分类',
    education_std   VARCHAR(20) COMMENT '标准化学历: 大专/本科/硕士/博士',
    experience_min  INT COMMENT '最低经验年限',
    experience_max  INT COMMENT '最高经验年限',
    salary_min      DECIMAL(10,2),
    salary_max      DECIMAL(10,2),
    salary_avg      DECIMAL(10,2) COMMENT '(min+max)/2',
    skill_tags      JSON COMMENT '技能标签数组',
    publish_date    DATE,
    source_site     VARCHAR(50),
    etl_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL处理时间',
    UNIQUE KEY uk_job (job_id),
    INDEX idx_city (city_std),
    INDEX idx_industry (industry_std),
    INDEX idx_education (education_std),
    INDEX idx_publish (publish_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数仓明细宽表(DWD层)';

-- ============================================================
-- 21. 数仓 — 按日城市汇总 (DWS)
-- ============================================================
CREATE TABLE IF NOT EXISTS dws_daily_city_summary (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date       DATE NOT NULL,
    city            VARCHAR(50) NOT NULL,
    job_count       INT DEFAULT 0,
    avg_salary_min  DECIMAL(10,2),
    avg_salary_max  DECIMAL(10,2),
    new_jobs_count  INT DEFAULT 0 COMMENT '当日新增岗位',
    top_skills      JSON COMMENT '当日热门技能TOP5',
    etl_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_city (stat_date, city),
    INDEX idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数仓按日城市汇总(DWS层)';

-- ============================================================
-- 22. 数仓 — 按月行业汇总 (DWS)
-- ============================================================
CREATE TABLE IF NOT EXISTS dws_monthly_industry_summary (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_month      VARCHAR(7) NOT NULL COMMENT '格式: 2026-04',
    industry        VARCHAR(100) NOT NULL,
    job_count       INT DEFAULT 0,
    avg_salary_min  DECIMAL(10,2),
    avg_salary_max  DECIMAL(10,2),
    growth_rate     DECIMAL(6,2) COMMENT '环比增长率(%)',
    top_skills      JSON COMMENT '行业热门技能TOP10',
    etl_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_month_industry (stat_month, industry),
    INDEX idx_month (stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数仓按月行业汇总(DWS层)';

-- ============================================================
-- 23. 数仓 — 看板KPI快照 (ADS)
-- ============================================================
CREATE TABLE IF NOT EXISTS ads_dashboard_kpi (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date       DATE NOT NULL UNIQUE,
    total_jobs      BIGINT,
    active_jobs     BIGINT,
    total_companies BIGINT,
    avg_salary      DECIMAL(10,2),
    median_salary   DECIMAL(10,2),
    top_city        VARCHAR(50),
    top_industry    VARCHAR(100),
    top_skill       VARCHAR(100),
    new_jobs_7d     INT COMMENT '近7天新增',
    salary_trend    VARCHAR(10) COMMENT 'UP/DOWN/FLAT',
    etl_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数仓看板KPI(ADS层)';

-- ============================================================
-- 24. 学校课程/教学大纲表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_curriculum (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_name     VARCHAR(200) NOT NULL COMMENT '课程名称',
    course_code     VARCHAR(50) COMMENT '课程编码',
    department      VARCHAR(100) COMMENT '开课院系',
    major           VARCHAR(100) COMMENT '所属专业',
    credit          DECIMAL(3,1) COMMENT '学分',
    semester        VARCHAR(20) COMMENT '开课学期: 2025-2026-1',
    description     TEXT COMMENT '课程简介/教学大纲',
    keywords        JSON COMMENT '课程关键词（手动或自动提取）',
    is_active       TINYINT DEFAULT 1,
    uploaded_by     BIGINT COMMENT '上传人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_major (major),
    INDEX idx_dept (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校课程/教学大纲表';

-- ============================================================
-- 25. 课程-技能映射表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_curriculum_skill_mapping (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    curriculum_id   BIGINT NOT NULL,
    skill_id        BIGINT NOT NULL,
    relevance       DECIMAL(3,2) DEFAULT 1.00 COMMENT '相关度权重 0-1',
    source          VARCHAR(20) DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL',
    UNIQUE KEY uk_curriculum_skill (curriculum_id, skill_id),
    FOREIGN KEY (curriculum_id) REFERENCES biz_curriculum(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES biz_skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程技能映射表';

-- ============================================================
-- 26. 站内通知表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_notification (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT,
    notify_type     VARCHAR(50) COMMENT 'JOB_PUSH/REPORT_READY/SYSTEM',
    ref_id          BIGINT COMMENT '关联ID（如岗位ID/报告ID）',
    is_read         TINYINT DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_read (is_read),
    INDEX idx_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

-- ============================================================
-- 27. Webhook端点注册表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_webhook_endpoint (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    endpoint_url    VARCHAR(500) NOT NULL,
    secret_key      VARCHAR(128) NOT NULL COMMENT 'HMAC签名密钥',
    event_types     JSON COMMENT '订阅事件类型数组',
    is_active       TINYINT DEFAULT 1,
    last_triggered  DATETIME,
    fail_count      INT DEFAULT 0 COMMENT '连续失败次数',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook端点注册表';

-- ============================================================
-- 28. Webhook投递记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_webhook_delivery (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    endpoint_id     BIGINT NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload         JSON NOT NULL,
    http_status     INT COMMENT '响应状态码',
    response_body   TEXT COMMENT '响应体(截断)',
    response_time   INT COMMENT '响应耗时(ms)',
    attempt         INT DEFAULT 1 COMMENT '第几次尝试',
    status          VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (endpoint_id) REFERENCES biz_webhook_endpoint(id) ON DELETE CASCADE,
    INDEX idx_endpoint (endpoint_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook投递记录表';

-- ============================================================
-- 29. API Key管理表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_api_key (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '所属用户',
    api_key         VARCHAR(64) NOT NULL UNIQUE COMMENT 'API密钥',
    key_name        VARCHAR(100) COMMENT '密钥备注名',
    permissions     JSON COMMENT '权限范围',
    rate_limit_qps  INT DEFAULT 10 COMMENT 'QPS限制',
    daily_quota     INT DEFAULT 1000 COMMENT '日调用配额',
    is_active       TINYINT DEFAULT 1,
    expires_at      DATETIME COMMENT '过期时间',
    last_used_at    DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_key (api_key),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key管理表';

-- ============================================================
-- 30. API调用日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_api_call_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key_id      BIGINT,
    endpoint        VARCHAR(200) NOT NULL,
    method          VARCHAR(10) NOT NULL,
    request_params  TEXT,
    response_code   INT,
    response_time   INT COMMENT '耗时(ms)',
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_key (api_key_id),
    INDEX idx_time (created_at),
    INDEX idx_endpoint (endpoint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API调用日志表';

-- ============================================================
-- 31. 技能关系表（知识图谱）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_skill_relation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_id_a      BIGINT NOT NULL,
    skill_id_b      BIGINT NOT NULL,
    relation_type   VARCHAR(30) NOT NULL COMMENT 'CO_OCCUR/PARENT_CHILD/SIMILAR/PREREQUISITE',
    weight          DECIMAL(5,2) DEFAULT 1.00 COMMENT '关系权重',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_pair (skill_id_a, skill_id_b, relation_type),
    FOREIGN KEY (skill_id_a) REFERENCES biz_skill(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id_b) REFERENCES biz_skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能关系表(知识图谱)';

-- ============================================================
-- 32. 职业路径表（知识图谱）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_career_path (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_title_from  VARCHAR(200) NOT NULL COMMENT '起始岗位',
    job_title_to    VARCHAR(200) NOT NULL COMMENT '目标岗位',
    transition_type VARCHAR(30) COMMENT 'PROMOTION/LATERAL/PIVOT',
    avg_years       DECIMAL(3,1) COMMENT '平均过渡年限',
    required_skills JSON COMMENT '需补充技能',
    frequency       INT DEFAULT 0 COMMENT '从数据中观察到的频次',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_from (job_title_from),
    INDEX idx_to (job_title_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职业路径表(知识图谱)';

-- ============================================================
-- 33. 定时报告计划表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_report_schedule (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    schedule_name   VARCHAR(200) NOT NULL,
    report_type     VARCHAR(50) NOT NULL COMMENT 'SALARY/SKILL/INDUSTRY/COMPREHENSIVE',
    cron_expr       VARCHAR(50) NOT NULL COMMENT 'Cron表达式',
    params          JSON COMMENT '报告参数',
    is_active       TINYINT DEFAULT 1,
    last_run_at     DATETIME,
    next_run_at     DATETIME,
    created_by      BIGINT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时报告计划表';

-- ============================================================
-- 初始数据：数据源配置
-- ============================================================
INSERT IGNORE INTO biz_data_source (source_name, source_code, base_url, health_status)
VALUES
    ('前程无忧', '51job', 'https://search.51job.com', 'HEALTHY'),
    ('智联招聘', 'zhaopin', 'https://fe-api.zhaopin.com', 'UNKNOWN'),
    ('BOSS直聘', 'boss', 'https://www.zhipin.com', 'UNKNOWN');
-- ============================================================
-- 34. 正式版必须补齐的维表/关系表与结构化字段
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

CREATE TABLE IF NOT EXISTS user_report_preference (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    preference_name      VARCHAR(100) NOT NULL,
    major_id             BIGINT,
    region_code          VARCHAR(20),
    province_code        VARCHAR(20),
    city_code            VARCHAR(20),
    salary_min           INT,
    salary_max           INT,
    job_category_id      BIGINT,
    company_name_keyword VARCHAR(100),
    company_industry     VARCHAR(100),
    company_size         VARCHAR(50),
    company_finance      VARCHAR(50),
    sort_type            VARCHAR(50),
    is_default           TINYINT      NOT NULL DEFAULT 0 COMMENT '0-否 1-是',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS biz_report_snapshot (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id       BIGINT   NOT NULL,
    task_id         BIGINT,
    user_id         BIGINT,
    filter_snapshot JSON     NOT NULL,
    data_snapshot   JSON,
    data_start_time DATETIME,
    data_end_time   DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS job_label_dict (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    label_name       VARCHAR(100) NOT NULL,
    label_code       VARCHAR(64),
    label_type       VARCHAR(50)  NOT NULL COMMENT 'skill/industry/tool/language/framework/other',
    parent_id        BIGINT,
    alias_name       VARCHAR(255),
    description      VARCHAR(255),
    sort_no          INT DEFAULT 0,
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_label_name_type (label_name, label_type),
    UNIQUE KEY uk_label_code (label_code),
    INDEX idx_label_parent (parent_id),
    CONSTRAINT fk_job_label_dict_parent
        FOREIGN KEY (parent_id) REFERENCES job_label_dict(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位标签字典表';

CREATE TABLE IF NOT EXISTS job_label_rel (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_posting_id   BIGINT       NOT NULL,
    label_id         BIGINT       NOT NULL,
    source_type      VARCHAR(32) COMMENT 'crawl_extract/manual/ai_extract',
    confidence_score DECIMAL(5,2),
    is_core          TINYINT      NOT NULL DEFAULT 0 COMMENT '0-否 1-是',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_label_rel (job_posting_id, label_id),
    INDEX idx_job_label_rel_label (label_id),
    CONSTRAINT fk_job_label_rel_job
        FOREIGN KEY (job_posting_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_label_rel_label
        FOREIGN KEY (label_id) REFERENCES job_label_dict(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位标签关联表';

CREATE TABLE IF NOT EXISTS job_welfare_dict (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    welfare_name  VARCHAR(100) NOT NULL,
    welfare_code  VARCHAR(64),
    welfare_type  VARCHAR(50) COMMENT 'insurance/holiday/subsidy/travel/bonus/other',
    parent_id     BIGINT,
    description   VARCHAR(255),
    sort_no       INT DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_welfare_name (welfare_name),
    UNIQUE KEY uk_welfare_code (welfare_code),
    INDEX idx_welfare_parent (parent_id),
    CONSTRAINT fk_job_welfare_dict_parent
        FOREIGN KEY (parent_id) REFERENCES job_welfare_dict(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='福利字典表';

CREATE TABLE IF NOT EXISTS job_welfare_rel (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_posting_id BIGINT      NOT NULL,
    welfare_id     BIGINT      NOT NULL,
    source_type    VARCHAR(32) COMMENT 'crawl_extract/manual/ai_extract',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_job_welfare_rel (job_posting_id, welfare_id),
    INDEX idx_job_welfare_rel_welfare (welfare_id),
    CONSTRAINT fk_job_welfare_rel_job
        FOREIGN KEY (job_posting_id) REFERENCES biz_job_posting(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_welfare_rel_welfare
        FOREIGN KEY (welfare_id) REFERENCES job_welfare_dict(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位福利关联表';

CREATE TABLE IF NOT EXISTS major_job_match_rule (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    major_id        BIGINT       NOT NULL,
    job_category_id BIGINT       NOT NULL,
    match_weight    DECIMAL(5,2) NOT NULL,
    core_skills     VARCHAR(255),
    rule_source     VARCHAR(50) COMMENT 'manual/ai/statistical',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    remark          VARCHAR(255),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_major_job_rule (major_id, job_category_id),
    CONSTRAINT fk_major_job_rule_major
        FOREIGN KEY (major_id) REFERENCES dim_major(id) ON DELETE CASCADE,
    CONSTRAINT fk_major_job_rule_category
        FOREIGN KEY (job_category_id) REFERENCES dim_job_category(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业-岗位匹配规则表';

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

ALTER TABLE biz_company
    ADD CONSTRAINT fk_biz_company_region_code
        FOREIGN KEY (region_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_biz_company_province_code
        FOREIGN KEY (province_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_biz_company_city_code
        FOREIGN KEY (city_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL;

ALTER TABLE biz_job_posting
    ADD COLUMN region_code VARCHAR(20) NULL AFTER company_name,
    ADD COLUMN province_code VARCHAR(20) NULL AFTER region_code,
    ADD COLUMN city_code VARCHAR(20) NULL AFTER province_code,
    ADD COLUMN job_category_id BIGINT NULL AFTER industry_name;

ALTER TABLE biz_job_posting
    ADD CONSTRAINT fk_biz_job_posting_region_code
        FOREIGN KEY (region_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_biz_job_posting_province_code
        FOREIGN KEY (province_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_biz_job_posting_city_code
        FOREIGN KEY (city_code) REFERENCES dim_region(region_code)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_biz_job_posting_job_category
        FOREIGN KEY (job_category_id) REFERENCES dim_job_category(id)
        ON DELETE SET NULL;
