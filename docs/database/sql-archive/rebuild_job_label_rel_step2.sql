USE career_platform;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

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
    (SELECT COUNT(*) FROM job_label_rel) AS label_rel_rows,
    (SELECT COUNT(DISTINCT job_posting_id) FROM job_label_rel) AS label_rel_job_rows,
    (SELECT COUNT(*) FROM job_label_dict) AS label_dict_rows;
