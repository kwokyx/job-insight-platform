USE career_platform;

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE job_label_rel;
TRUNCATE TABLE job_welfare_rel;
TRUNCATE TABLE biz_recommendation_result;
TRUNCATE TABLE biz_recommend_result;
TRUNCATE TABLE biz_user_job_action;
TRUNCATE TABLE biz_job_skill;
TRUNCATE TABLE biz_job_history;
TRUNCATE TABLE biz_job_posting;
TRUNCATE TABLE biz_company;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO biz_company (
    company_name,
    company_size,
    company_finance,
    logo_url,
    created_at,
    updated_at
)
SELECT
    t.company_name,
    t.company_size,
    t.company_finance,
    t.company_logo AS logo_url,
    NOW(),
    NOW()
FROM (
    SELECT
        company_name,
        NULLIF(company_size, '') AS company_size,
        NULLIF(company_finance, '') AS company_finance,
        NULLIF(company_logo, '') AS company_logo,
        ROW_NUMBER() OVER (
            PARTITION BY company_name
            ORDER BY crawl_time DESC, url_obj_id DESC
        ) AS rn
    FROM crawl_job_posting
    WHERE company_name IS NOT NULL
      AND TRIM(company_name) <> ''
) t
WHERE t.rn = 1;

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
    url_obj_id,
    title,
    company_name,
    company_size,
    company_finance,
    NULL AS region_code,
    NULL AS province_code,
    NULL AS city_code,
    job_city,
    job_classification,
    NULL AS job_category_id,
    education_need,
    experience_year,
    salary_min,
    salary_max,
    salary_raw,
    job_welfare,
    CASE
        WHEN job_labels IS NULL OR TRIM(job_labels) = '' THEN NULL
        ELSE JSON_ARRAY(TRIM(BOTH '"' FROM REPLACE(REPLACE(job_labels, '\\', '\\\\'), '"', '\\"')))
    END AS job_labels,
    position_info,
    url,
    DATE(publish_date),
    crawl_time,
    crawl_update_time
FROM crawl_job_posting;

SELECT
    (SELECT COUNT(*) FROM biz_company) AS company_rows,
    (SELECT COUNT(*) FROM biz_job_posting) AS job_rows;
