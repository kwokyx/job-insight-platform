USE career_platform;

INSERT INTO biz_company (
    company_name,
    company_size,
    company_finance,
    logo_url,
    created_at,
    updated_at
)
SELECT
    src.company_name,
    src.company_size,
    src.company_finance,
    src.logo_url,
    NOW(),
    NOW()
FROM (
    SELECT
        company_name,
        NULLIF(company_size, '') AS company_size,
        NULLIF(company_finance, '') AS company_finance,
        NULLIF(company_logo, '') AS logo_url,
        ROW_NUMBER() OVER (
            PARTITION BY company_name
            ORDER BY crawl_time DESC, url_obj_id DESC
        ) AS rn
    FROM crawl_job_posting
    WHERE company_name IS NOT NULL
      AND TRIM(company_name) <> ''
) src
WHERE src.rn = 1
ON DUPLICATE KEY UPDATE
    company_size = VALUES(company_size),
    company_finance = VALUES(company_finance),
    logo_url = VALUES(logo_url),
    updated_at = NOW();

INSERT INTO biz_job_posting (
    url_obj_id,
    title,
    company_name,
    company_size,
    company_finance,
    region_code,
    province_code,
    city_code,
    job_city,
    job_classification,
    job_category_id,
    education_need,
    experience_year,
    salary_min,
    salary_max,
    salary_raw,
    job_welfare,
    job_labels,
    position_info,
    url,
    publish_date,
    crawl_time,
    crawl_update_time
)
SELECT
    src.url_obj_id,
    src.title,
    src.company_name,
    src.company_size,
    src.company_finance,
    NULL AS region_code,
    NULL AS province_code,
    NULL AS city_code,
    src.job_city,
    src.job_classification,
    NULL AS job_category_id,
    src.education_need,
    src.experience_year,
    CASE
        WHEN src.salary_min IS NULL OR src.salary_min <= 0 THEN NULL
        WHEN src.salary_min > 200 THEN ROUND(src.salary_min / 1000, 2)
        ELSE src.salary_min
    END AS salary_min,
    CASE
        WHEN src.salary_max IS NULL OR src.salary_max <= 0 THEN NULL
        WHEN src.salary_max > 200 THEN ROUND(src.salary_max / 1000, 2)
        ELSE src.salary_max
    END AS salary_max,
    src.salary_raw,
    src.job_welfare,
    CASE
        WHEN src.job_labels IS NULL OR TRIM(src.job_labels) = '' THEN NULL
        ELSE JSON_ARRAY(TRIM(BOTH '"' FROM REPLACE(REPLACE(src.job_labels, '\\', '\\\\'), '"', '\\"')))
    END AS job_labels,
    src.position_info,
    src.url,
    DATE(src.publish_date),
    src.crawl_time,
    src.crawl_update_time
FROM crawl_job_posting src
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    company_name = VALUES(company_name),
    company_size = VALUES(company_size),
    company_finance = VALUES(company_finance),
    job_city = VALUES(job_city),
    job_classification = VALUES(job_classification),
    education_need = VALUES(education_need),
    experience_year = VALUES(experience_year),
    salary_min = VALUES(salary_min),
    salary_max = VALUES(salary_max),
    salary_raw = VALUES(salary_raw),
    job_welfare = VALUES(job_welfare),
    job_labels = VALUES(job_labels),
    position_info = VALUES(position_info),
    url = VALUES(url),
    publish_date = VALUES(publish_date),
    crawl_time = VALUES(crawl_time),
    crawl_update_time = VALUES(crawl_update_time);

SELECT
    (SELECT COUNT(*) FROM crawl_job_posting) AS crawl_rows,
    (SELECT COUNT(*) FROM biz_company) AS company_rows,
    (SELECT COUNT(*) FROM biz_job_posting) AS job_rows;
