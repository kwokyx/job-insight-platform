USE career_platform;

INSERT INTO dim_region (
    region_code,
    region_name,
    region_level,
    parent_code,
    full_name,
    sort_no,
    status,
    created_at,
    updated_at
)
SELECT
    CONCAT(
        'CITY_',
        LPAD(
            ROW_NUMBER() OVER (ORDER BY city_name)
            + COALESCE((SELECT COUNT(*) FROM dim_region WHERE region_code LIKE 'CITY_%'), 0),
            5,
            '0'
        )
    ) AS region_code,
    city_name,
    3,
    NULL,
    city_name,
    0,
    1,
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT TRIM(job_city) AS city_name
    FROM biz_job_posting
    WHERE job_city IS NOT NULL
      AND TRIM(job_city) <> ''
      AND NOT EXISTS (
          SELECT 1
          FROM dim_region r
          WHERE r.region_level = 3
            AND r.region_name = TRIM(biz_job_posting.job_city)
      )
) t;

UPDATE biz_job_posting jp
JOIN dim_region r
  ON r.region_level = 3
 AND r.region_name = jp.job_city
SET jp.city_code = r.region_code
WHERE jp.job_city IS NOT NULL
  AND TRIM(jp.job_city) <> '';

UPDATE biz_company bc
JOIN (
    SELECT company_name, job_city
    FROM (
        SELECT
            company_name,
            job_city,
            ROW_NUMBER() OVER (
                PARTITION BY company_name
                ORDER BY crawl_time DESC, id DESC
            ) AS rn
        FROM biz_job_posting
        WHERE company_name IS NOT NULL
          AND TRIM(company_name) <> ''
          AND job_city IS NOT NULL
          AND TRIM(job_city) <> ''
    ) x
    WHERE rn = 1
) latest
  ON latest.company_name = bc.company_name
JOIN dim_region r
  ON r.region_level = 3
 AND r.region_name = latest.job_city
SET bc.city_code = r.region_code;

INSERT INTO dim_job_category (
    category_code,
    category_name,
    parent_id,
    category_level,
    description,
    status,
    created_at,
    updated_at
)
SELECT
    CONCAT(
        'CAT_',
        LPAD(
            ROW_NUMBER() OVER (ORDER BY category_name)
            + COALESCE((SELECT COUNT(*) FROM dim_job_category WHERE category_code LIKE 'CAT_%'), 0),
            5,
            '0'
        )
    ) AS category_code,
    category_name,
    NULL,
    1,
    category_name,
    1,
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT TRIM(job_classification) AS category_name
    FROM biz_job_posting
    WHERE job_classification IS NOT NULL
      AND TRIM(job_classification) <> ''
      AND NOT EXISTS (
          SELECT 1
          FROM dim_job_category c
          WHERE c.category_level = 1
            AND c.category_name = TRIM(biz_job_posting.job_classification)
      )
) t;

UPDATE biz_job_posting jp
JOIN dim_job_category c
  ON c.category_level = 1
 AND c.category_name = jp.job_classification
SET jp.job_category_id = c.id
WHERE jp.job_classification IS NOT NULL
  AND TRIM(jp.job_classification) <> '';

DROP TEMPORARY TABLE IF EXISTS tmp_label_tokens;
CREATE TEMPORARY TABLE tmp_label_tokens (
    url_obj_id   VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    label_name   VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_label_tokens (url_obj_id, label_name)
WITH RECURSIVE label_split AS (
    SELECT
        cj.url_obj_id,
        TRIM(
            SUBSTRING_INDEX(
                REPLACE(REPLACE(cj.job_labels, CHAR(13), ' '), CHAR(10), ' '),
                ',',
                1
            )
        ) AS label_name,
        CASE
            WHEN INSTR(REPLACE(REPLACE(cj.job_labels, CHAR(13), ' '), CHAR(10), ' '), ',') > 0
            THEN SUBSTRING(
                REPLACE(REPLACE(cj.job_labels, CHAR(13), ' '), CHAR(10), ' '),
                INSTR(REPLACE(REPLACE(cj.job_labels, CHAR(13), ' '), CHAR(10), ' '), ',') + 1
            )
            ELSE ''
        END AS rest
    FROM crawl_job_posting cj
    WHERE cj.job_labels IS NOT NULL
      AND TRIM(cj.job_labels) <> ''

    UNION ALL

    SELECT
        url_obj_id,
        TRIM(SUBSTRING_INDEX(rest, ',', 1)) AS label_name,
        CASE
            WHEN INSTR(rest, ',') > 0
            THEN SUBSTRING(rest, INSTR(rest, ',') + 1)
            ELSE ''
        END AS rest
    FROM label_split
    WHERE rest <> ''
)
SELECT url_obj_id, label_name
FROM label_split
WHERE label_name <> '';

DROP TEMPORARY TABLE IF EXISTS tmp_label_json;
CREATE TEMPORARY TABLE tmp_label_json (
    url_obj_id     VARCHAR(100) COLLATE utf8mb4_unicode_ci PRIMARY KEY,
    labels_json    JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_label_json (url_obj_id, labels_json)
SELECT
    url_obj_id,
    JSON_ARRAYAGG(label_name)
FROM (
    SELECT DISTINCT url_obj_id, label_name
    FROM tmp_label_tokens
    WHERE label_name <> ''
) t
GROUP BY url_obj_id;

UPDATE biz_job_posting jp
LEFT JOIN tmp_label_json tj
  ON tj.url_obj_id COLLATE utf8mb4_unicode_ci = jp.url_obj_id COLLATE utf8mb4_unicode_ci
SET jp.job_labels = tj.labels_json;

DELETE FROM job_label_rel;
DELETE FROM job_label_dict;

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
FROM tmp_label_tokens t
WHERE NOT EXISTS (
    SELECT 1
    FROM job_label_dict d
    WHERE d.label_name COLLATE utf8mb4_unicode_ci = t.label_name COLLATE utf8mb4_unicode_ci
      AND d.label_type = 'other'
);

INSERT IGNORE INTO job_label_rel (
    job_posting_id,
    label_id,
    source_type,
    confidence_score,
    is_core,
    created_at
)
SELECT
    jp.id,
    d.id,
    'crawl_extract',
    NULL,
    0,
    NOW()
FROM tmp_label_tokens t
JOIN biz_job_posting jp
  ON jp.url_obj_id COLLATE utf8mb4_unicode_ci = t.url_obj_id COLLATE utf8mb4_unicode_ci
JOIN job_label_dict d
  ON d.label_name COLLATE utf8mb4_unicode_ci = t.label_name COLLATE utf8mb4_unicode_ci
 AND d.label_type = 'other';

DROP TEMPORARY TABLE IF EXISTS tmp_welfare_tokens;
CREATE TEMPORARY TABLE tmp_welfare_tokens (
    url_obj_id      VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    welfare_name    VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_welfare_tokens (url_obj_id, welfare_name)
WITH RECURSIVE welfare_split AS (
    SELECT
        cj.url_obj_id,
        TRIM(
            SUBSTRING_INDEX(
                REPLACE(REPLACE(cj.job_welfare, CHAR(13), ' '), CHAR(10), ' '),
                ',',
                1
            )
        ) AS welfare_name,
        CASE
            WHEN INSTR(REPLACE(REPLACE(cj.job_welfare, CHAR(13), ' '), CHAR(10), ' '), ',') > 0
            THEN SUBSTRING(
                REPLACE(REPLACE(cj.job_welfare, CHAR(13), ' '), CHAR(10), ' '),
                INSTR(REPLACE(REPLACE(cj.job_welfare, CHAR(13), ' '), CHAR(10), ' '), ',') + 1
            )
            ELSE ''
        END AS rest
    FROM crawl_job_posting cj
    WHERE cj.job_welfare IS NOT NULL
      AND TRIM(cj.job_welfare) <> ''

    UNION ALL

    SELECT
        url_obj_id,
        TRIM(SUBSTRING_INDEX(rest, ',', 1)) AS welfare_name,
        CASE
            WHEN INSTR(rest, ',') > 0
            THEN SUBSTRING(rest, INSTR(rest, ',') + 1)
            ELSE ''
        END AS rest
    FROM welfare_split
    WHERE rest <> ''
)
SELECT url_obj_id, welfare_name
FROM welfare_split
WHERE welfare_name <> '';

DELETE FROM job_welfare_rel;
DELETE FROM job_welfare_dict;

INSERT IGNORE INTO job_welfare_dict (
    welfare_name,
    welfare_code,
    welfare_type,
    parent_id,
    description,
    sort_no,
    status,
    created_at,
    updated_at
)
SELECT DISTINCT
    t.welfare_name,
    CONCAT('WEL_', UPPER(SUBSTRING(MD5(t.welfare_name), 1, 16))) AS welfare_code,
    'other',
    NULL,
    t.welfare_name,
    0,
    1,
    NOW(),
    NOW()
FROM tmp_welfare_tokens t
WHERE NOT EXISTS (
    SELECT 1
    FROM job_welfare_dict d
    WHERE d.welfare_name COLLATE utf8mb4_unicode_ci = t.welfare_name COLLATE utf8mb4_unicode_ci
);

INSERT IGNORE INTO job_welfare_rel (
    job_posting_id,
    welfare_id,
    source_type,
    created_at
)
SELECT
    jp.id,
    d.id,
    'crawl_extract',
    NOW()
FROM tmp_welfare_tokens t
JOIN biz_job_posting jp
  ON jp.url_obj_id COLLATE utf8mb4_unicode_ci = t.url_obj_id COLLATE utf8mb4_unicode_ci
JOIN job_welfare_dict d
  ON d.welfare_name COLLATE utf8mb4_unicode_ci = t.welfare_name COLLATE utf8mb4_unicode_ci;

SELECT
    (SELECT COUNT(*) FROM dim_region) AS region_rows,
    (SELECT COUNT(*) FROM dim_job_category) AS category_rows,
    (SELECT COUNT(*) FROM job_label_dict) AS label_dict_rows,
    (SELECT COUNT(*) FROM job_label_rel) AS label_rel_rows,
    (SELECT COUNT(*) FROM job_welfare_dict) AS welfare_dict_rows,
    (SELECT COUNT(*) FROM job_welfare_rel) AS welfare_rel_rows;
