DROP TABLE IF EXISTS biz_job_posting;

CREATE TABLE IF NOT EXISTS biz_job_posting (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    url                 VARCHAR(500) COMMENT '招聘职位的原始链接地址',
    url_obj_id          VARCHAR(100) COMMENT '该条招聘记录的唯一标识ID',
    title               VARCHAR(255) NOT NULL COMMENT '招聘职位名称',
    salary_min          DECIMAL(10,2) COMMENT '月薪最低值（元）',
    salary_max          DECIMAL(10,2) COMMENT '月薪最高值（元）',
    salary_raw          VARCHAR(100) COMMENT '原始展示的薪资范围文本',
    job_city            VARCHAR(100) COMMENT '工作所在城市',
    experience_year     VARCHAR(50) COMMENT '要求的工作经验年限',
    education_need      VARCHAR(50) COMMENT '要求的学历水平',
    publish_date        DATE COMMENT '招聘信息发布日期',
    job_welfare         TEXT COMMENT '职位福利待遇（如五险一金、包餐等）',
    job_labels          JSON COMMENT '职位相关标签（如技能、行业方向）',
    position_info       TEXT COMMENT '职位详细描述，包括岗位职责和任职要求',
    job_classification  VARCHAR(100) COMMENT '职位所属分类（如化工、CNC/数控操作）',
    company_name        VARCHAR(255) NOT NULL COMMENT '招聘公司名称',
    company_size        VARCHAR(50) COMMENT '公司规模（人数范围）',
    company_finance     VARCHAR(50) COMMENT '公司融资阶段（为空表示未提供）',
    crawl_time          DATETIME NOT NULL COMMENT '数据抓取时间',
    crawl_update_time   DATETIME COMMENT '数据最后更新时间',

    UNIQUE KEY uk_url_obj_id (url_obj_id),
    INDEX idx_title (title),
    INDEX idx_job_city (job_city),
    INDEX idx_job_classification (job_classification),
    INDEX idx_education_need (education_need),
    INDEX idx_salary (salary_min, salary_max),
    INDEX idx_publish_date (publish_date),
    INDEX idx_crawl_time (crawl_time),
    FULLTEXT INDEX ft_position_info (position_info)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位主表';
