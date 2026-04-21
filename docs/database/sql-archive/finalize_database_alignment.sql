USE career_platform;

ALTER TABLE biz_job_posting
    ADD COLUMN company_id BIGINT NULL,
    ADD COLUMN region VARCHAR(100) NULL,
    ADD COLUMN city VARCHAR(100) NULL,
    ADD COLUMN district VARCHAR(100) NULL,
    ADD COLUMN industry_id BIGINT NULL,
    ADD COLUMN industry_name VARCHAR(100) NULL,
    ADD COLUMN employment_type VARCHAR(50) NULL,
    ADD COLUMN requirements TEXT NULL,
    ADD COLUMN source_site VARCHAR(100) NULL,
    ADD COLUMN source_url VARCHAR(500) NULL,
    ADD COLUMN data_quality TINYINT NULL,
    ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ALGORITHM=INSTANT;

UPDATE biz_job_posting jp
LEFT JOIN biz_company bc
       ON bc.company_name = jp.company_name
SET jp.company_id = COALESCE(jp.company_id, bc.id),
    jp.region = COALESCE(jp.region, jp.job_city),
    jp.city = COALESCE(jp.city, jp.job_city),
    jp.industry_name = COALESCE(jp.industry_name, jp.job_classification),
    jp.requirements = COALESCE(jp.requirements, jp.position_info),
    jp.source_url = COALESCE(jp.source_url, jp.url),
    jp.source_site = COALESCE(
        jp.source_site,
        CASE
            WHEN jp.url LIKE '%zhaopin%' THEN 'zhaopin'
            WHEN jp.url LIKE '%51job%' THEN '51job'
            WHEN jp.url LIKE '%zhipin%' THEN 'boss'
            ELSE 'unknown'
        END
    ),
    jp.data_quality = COALESCE(
        jp.data_quality,
        CASE
            WHEN (
                (CASE WHEN jp.title IS NOT NULL AND TRIM(jp.title) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.company_name IS NOT NULL AND TRIM(jp.company_name) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_city IS NOT NULL AND TRIM(jp.job_city) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.salary_raw IS NOT NULL AND TRIM(jp.salary_raw) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.education_need IS NOT NULL AND TRIM(jp.education_need) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.experience_year IS NOT NULL AND TRIM(jp.experience_year) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.position_info IS NOT NULL AND TRIM(jp.position_info) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_labels IS NOT NULL THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_welfare IS NOT NULL AND TRIM(jp.job_welfare) <> '' THEN 1 ELSE 0 END)
            ) >= 8 THEN 5
            WHEN (
                (CASE WHEN jp.title IS NOT NULL AND TRIM(jp.title) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.company_name IS NOT NULL AND TRIM(jp.company_name) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_city IS NOT NULL AND TRIM(jp.job_city) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.salary_raw IS NOT NULL AND TRIM(jp.salary_raw) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.education_need IS NOT NULL AND TRIM(jp.education_need) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.experience_year IS NOT NULL AND TRIM(jp.experience_year) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.position_info IS NOT NULL AND TRIM(jp.position_info) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_labels IS NOT NULL THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_welfare IS NOT NULL AND TRIM(jp.job_welfare) <> '' THEN 1 ELSE 0 END)
            ) >= 6 THEN 4
            WHEN (
                (CASE WHEN jp.title IS NOT NULL AND TRIM(jp.title) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.company_name IS NOT NULL AND TRIM(jp.company_name) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_city IS NOT NULL AND TRIM(jp.job_city) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.salary_raw IS NOT NULL AND TRIM(jp.salary_raw) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.education_need IS NOT NULL AND TRIM(jp.education_need) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.experience_year IS NOT NULL AND TRIM(jp.experience_year) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.position_info IS NOT NULL AND TRIM(jp.position_info) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_labels IS NOT NULL THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_welfare IS NOT NULL AND TRIM(jp.job_welfare) <> '' THEN 1 ELSE 0 END)
            ) >= 4 THEN 3
            WHEN (
                (CASE WHEN jp.title IS NOT NULL AND TRIM(jp.title) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.company_name IS NOT NULL AND TRIM(jp.company_name) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_city IS NOT NULL AND TRIM(jp.job_city) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.salary_raw IS NOT NULL AND TRIM(jp.salary_raw) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.education_need IS NOT NULL AND TRIM(jp.education_need) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.experience_year IS NOT NULL AND TRIM(jp.experience_year) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.position_info IS NOT NULL AND TRIM(jp.position_info) <> '' THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_labels IS NOT NULL THEN 1 ELSE 0 END) +
                (CASE WHEN jp.job_welfare IS NOT NULL AND TRIM(jp.job_welfare) <> '' THEN 1 ELSE 0 END)
            ) >= 2 THEN 2
            ELSE 1
        END
    ),
    jp.is_active = COALESCE(jp.is_active, 1),
    jp.created_at = COALESCE(jp.created_at, jp.crawl_time, NOW()),
    jp.updated_at = COALESCE(jp.updated_at, jp.crawl_update_time, jp.crawl_time, NOW());

UPDATE biz_job_posting jp
LEFT JOIN dim_region city
       ON city.region_code = jp.city_code
LEFT JOIN dim_region province
       ON province.region_code = city.parent_code
LEFT JOIN dim_region region
       ON region.region_code = province.parent_code
SET jp.province_code = COALESCE(jp.province_code, province.region_code),
    jp.region_code = COALESCE(jp.region_code, region.region_code)
WHERE jp.city_code IS NOT NULL;

UPDATE biz_company bc
LEFT JOIN dim_region city
       ON city.region_code = bc.city_code
LEFT JOIN dim_region province
       ON province.region_code = city.parent_code
LEFT JOIN dim_region region
       ON region.region_code = province.parent_code
SET bc.province_code = COALESCE(bc.province_code, province.region_code),
    bc.region_code = COALESCE(bc.region_code, region.region_code)
WHERE bc.city_code IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_job_label_tokens;
CREATE TEMPORARY TABLE tmp_job_label_tokens (
    job_posting_id BIGINT NOT NULL,
    label_name     VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    PRIMARY KEY (job_posting_id, label_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO tmp_job_label_tokens (job_posting_id, label_name)
SELECT
    jp.id,
    CONVERT(TRIM(jt.label_name) USING utf8mb4) COLLATE utf8mb4_unicode_ci
FROM biz_job_posting jp
JOIN JSON_TABLE(
    jp.job_labels,
    '$[*]' COLUMNS(label_name VARCHAR(255) PATH '$')
) jt
WHERE jp.job_labels IS NOT NULL
  AND JSON_VALID(jp.job_labels)
  AND TRIM(jt.label_name) <> '';

INSERT IGNORE INTO job_label_dict (
    label_name,
    label_code,
    label_type,
    parent_id,
    alias_name,
    description,
    sort_no,
    status,
    created_at,
    updated_at
)
SELECT DISTINCT
    t.label_name,
    CONCAT('LBL_', UPPER(SUBSTRING(MD5(t.label_name), 1, 16))) AS label_code,
    'other',
    NULL,
    NULL,
    t.label_name,
    0,
    1,
    NOW(),
    NOW()
FROM tmp_job_label_tokens t;

DELETE FROM job_label_rel;

INSERT IGNORE INTO job_label_rel (
    job_posting_id,
    label_id,
    source_type,
    confidence_score,
    is_core,
    created_at
)
SELECT
    t.job_posting_id,
    d.id,
    'crawl_extract',
    NULL,
    0,
    NOW()
FROM tmp_job_label_tokens t
JOIN job_label_dict d
  ON d.label_name COLLATE utf8mb4_unicode_ci = t.label_name COLLATE utf8mb4_unicode_ci;

SELECT
    (SELECT COUNT(*) FROM biz_job_posting) AS job_rows,
    (SELECT COUNT(*) FROM biz_job_posting WHERE company_id IS NOT NULL) AS company_linked_rows,
    (SELECT COUNT(*) FROM biz_job_posting WHERE source_site IS NOT NULL AND source_site <> '') AS source_site_rows,
    (SELECT COUNT(*) FROM biz_job_posting WHERE source_url IS NOT NULL AND source_url <> '') AS source_url_rows,
    (SELECT COUNT(*) FROM biz_job_posting WHERE created_at IS NOT NULL) AS created_at_rows,
    (SELECT COUNT(*) FROM biz_job_posting WHERE updated_at IS NOT NULL) AS updated_at_rows,
    (SELECT COUNT(*) FROM job_label_rel) AS label_rel_rows,
    (SELECT COUNT(DISTINCT job_posting_id) FROM job_label_rel) AS label_rel_job_rows;
