USE career_platform;

DELETE FROM job_label_rel;
DELETE FROM job_welfare_rel;
DELETE FROM job_welfare_dict;

DROP TEMPORARY TABLE IF EXISTS tmp_label_tokens;
CREATE TEMPORARY TABLE tmp_label_tokens (
    job_posting_id BIGINT NOT NULL,
    label_name     VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_label_tokens (job_posting_id, label_name)
SELECT
    jp.id,
    CONVERT(TRIM(jt.label_name) USING utf8mb4) COLLATE utf8mb4_unicode_ci
FROM biz_job_posting jp
JOIN JSON_TABLE(
    jp.job_labels,
    '$[*]' COLUMNS(label_name VARCHAR(255) PATH '$')
) jt
WHERE jp.job_labels IS NOT NULL
  AND TRIM(jt.label_name) <> '';

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
FROM (
    SELECT DISTINCT job_posting_id, label_name
    FROM tmp_label_tokens
) t
JOIN job_label_dict d
  ON d.label_name COLLATE utf8mb4_unicode_ci = t.label_name COLLATE utf8mb4_unicode_ci
 AND d.label_type = 'other';

DROP TEMPORARY TABLE IF EXISTS tmp_welfare_tokens;
CREATE TEMPORARY TABLE tmp_welfare_tokens (
    job_posting_id BIGINT NOT NULL,
    welfare_name   VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_welfare_tokens (job_posting_id, welfare_name)
WITH RECURSIVE welfare_split AS (
    SELECT
        jp.id AS job_posting_id,
        TRIM(
            SUBSTRING_INDEX(
                REPLACE(REPLACE(jp.job_welfare, CHAR(13), ' '), CHAR(10), ' '),
                ',',
                1
            )
        ) COLLATE utf8mb4_unicode_ci AS welfare_name,
        CASE
            WHEN INSTR(REPLACE(REPLACE(jp.job_welfare, CHAR(13), ' '), CHAR(10), ' '), ',') > 0
            THEN SUBSTRING(
                REPLACE(REPLACE(jp.job_welfare, CHAR(13), ' '), CHAR(10), ' '),
                INSTR(REPLACE(REPLACE(jp.job_welfare, CHAR(13), ' '), CHAR(10), ' '), ',') + 1
            )
            ELSE ''
        END AS rest
    FROM biz_job_posting jp
    WHERE jp.job_welfare IS NOT NULL
      AND TRIM(jp.job_welfare) <> ''

    UNION ALL

    SELECT
        job_posting_id,
        TRIM(SUBSTRING_INDEX(rest, ',', 1)) COLLATE utf8mb4_unicode_ci AS welfare_name,
        CASE
            WHEN INSTR(rest, ',') > 0
            THEN SUBSTRING(rest, INSTR(rest, ',') + 1)
            ELSE ''
        END AS rest
    FROM welfare_split
    WHERE rest <> ''
)
SELECT job_posting_id, welfare_name
FROM welfare_split
WHERE welfare_name <> '';

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
FROM tmp_welfare_tokens t;

INSERT IGNORE INTO job_welfare_rel (
    job_posting_id,
    welfare_id,
    source_type,
    created_at
)
SELECT
    t.job_posting_id,
    d.id,
    'crawl_extract',
    NOW()
FROM (
    SELECT DISTINCT job_posting_id, welfare_name
    FROM tmp_welfare_tokens
) t
JOIN job_welfare_dict d
  ON d.welfare_name COLLATE utf8mb4_unicode_ci = t.welfare_name COLLATE utf8mb4_unicode_ci;

SELECT
    (SELECT COUNT(*) FROM job_label_rel) AS label_rel_rows,
    (SELECT COUNT(*) FROM job_welfare_dict) AS welfare_dict_rows,
    (SELECT COUNT(*) FROM job_welfare_rel) AS welfare_rel_rows;
