USE career_platform;

INSERT INTO dim_industry (
    industry_code,
    industry_name,
    parent_id,
    industry_level,
    status,
    created_at,
    updated_at
)
SELECT
    CONCAT('IND_', UPPER(SUBSTRING(MD5(src.industry_name), 1, 16))) AS industry_code,
    src.industry_name,
    NULL,
    1,
    1,
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT COALESCE(industry_name, job_classification) AS industry_name
    FROM biz_job_posting
    WHERE COALESCE(industry_name, job_classification) IS NOT NULL
      AND COALESCE(industry_name, job_classification) <> ''
) src
ON DUPLICATE KEY UPDATE
    industry_name = VALUES(industry_name),
    updated_at = NOW();

UPDATE biz_job_posting jp
JOIN dim_industry di
  ON di.industry_name = COALESCE(jp.industry_name, jp.job_classification)
SET jp.industry_id = di.id,
    jp.industry_name = COALESCE(jp.industry_name, jp.job_classification),
    jp.updated_at = NOW()
WHERE COALESCE(jp.industry_name, jp.job_classification) IS NOT NULL
  AND COALESCE(jp.industry_name, jp.job_classification) <> '';

UPDATE biz_company bc
JOIN (
    SELECT
        company_name,
        industry_id,
        industry_name,
        ROW_NUMBER() OVER (PARTITION BY company_name ORDER BY id DESC) AS rn
    FROM biz_job_posting
    WHERE company_name IS NOT NULL
      AND company_name <> ''
      AND industry_id IS NOT NULL
) latest
  ON latest.company_name = bc.company_name
 AND latest.rn = 1
SET bc.industry = COALESCE(bc.industry, latest.industry_name),
    bc.updated_at = NOW()
WHERE latest.industry_name IS NOT NULL
  AND latest.industry_name <> '';

SELECT
    (SELECT COUNT(*) FROM dim_industry) AS industry_count,
    (SELECT COUNT(*) FROM biz_job_posting WHERE industry_id IS NOT NULL) AS job_posting_industry_id_count,
    (SELECT COUNT(*) FROM biz_job_posting WHERE industry_name IS NOT NULL AND industry_name <> '') AS job_posting_industry_name_count,
    (SELECT COUNT(*) FROM biz_company WHERE industry IS NOT NULL AND industry <> '') AS company_industry_count;
