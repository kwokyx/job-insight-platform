CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255),
    company VARCHAR(255),
    region VARCHAR(100),
    industry VARCHAR(100),
    education VARCHAR(100),
    experience VARCHAR(100),
    salary_min DECIMAL(10,2) NULL,
    salary_max DECIMAL(10,2) NULL,
    salary_unit VARCHAR(50),
    employment_type VARCHAR(50),
    publish_date VARCHAR(50),
    source_site VARCHAR(100),
    source_url VARCHAR(500),
    crawl_time VARCHAR(50),
    description TEXT,
    skills JSON
);

CREATE TABLE IF NOT EXISTS indicators (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    indicator_name VARCHAR(255),
    metric_scope VARCHAR(100),
    period VARCHAR(50),
    region VARCHAR(100),
    value DECIMAL(10,2),
    unit VARCHAR(50),
    source_site VARCHAR(100),
    publish_date VARCHAR(50),
    source_url VARCHAR(500)
);

