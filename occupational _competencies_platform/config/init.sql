-- ============================================================
-- 职业能力大数据服务平台 — 完整数据库初始化脚本
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS career_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE career_platform;

-- ============================================================
-- 1. 用户表（二级角色：0-普通用户 1-管理员）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录账号',
    nickname        VARCHAR(100) COMMENT '显示昵称',
    email           VARCHAR(100) UNIQUE,
    phone           VARCHAR(20)  UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    role_type       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-普通用户 1-管理员',
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
    address         VARCHAR(500),
    website         VARCHAR(500),
    logo_url        VARCHAR(500),
    description     TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_industry (industry),
    INDEX idx_name (company_name)
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
        '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtGE1bF0LR3bNaGSSEP6',
        1, 1);
