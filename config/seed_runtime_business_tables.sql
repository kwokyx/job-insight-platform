USE career_platform;

CREATE TABLE IF NOT EXISTS `biz_job_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `job_id` BIGINT NOT NULL,
    `note` VARCHAR(255) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_job` (`user_id`, `job_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_job_id` (`job_id`),
    CONSTRAINT `fk_job_favorite_job`
        FOREIGN KEY (`job_id`) REFERENCES `biz_job_posting` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位收藏';

SET @default_user_id := (SELECT id FROM sys_user ORDER BY id LIMIT 1);
SET @default_major_id := COALESCE(
    (SELECT major_id FROM user_profile WHERE user_id = @default_user_id LIMIT 1),
    (SELECT id FROM dim_major WHERE major_code = 'MAJOR_CS' LIMIT 1),
    (SELECT id FROM dim_major ORDER BY id LIMIT 1)
);
SET @default_city_code := COALESCE(
    (SELECT target_city_code FROM user_profile WHERE user_id = @default_user_id LIMIT 1),
    (SELECT region_code FROM dim_region WHERE region_name = '北京' AND region_level = 3 LIMIT 1),
    (SELECT region_code FROM dim_region WHERE region_level = 3 ORDER BY id LIMIT 1)
);
SET @default_province_code := (
    SELECT parent_code FROM dim_region WHERE region_code = @default_city_code LIMIT 1
);
SET @default_region_code := (
    SELECT parent_code FROM dim_region WHERE region_code = @default_province_code LIMIT 1
);
SET @default_job_category_id := COALESCE(
    (SELECT target_job_category_id FROM user_profile WHERE user_id = @default_user_id LIMIT 1),
    (SELECT id FROM dim_job_category WHERE category_name = '数据分析师' LIMIT 1),
    (SELECT id FROM dim_job_category WHERE category_name = 'Java' LIMIT 1),
    (SELECT id FROM dim_job_category ORDER BY id LIMIT 1)
);
SET @default_salary_min := COALESCE(
    (SELECT expected_salary_min FROM user_profile WHERE user_id = @default_user_id LIMIT 1),
    5000
);
SET @default_salary_max := COALESCE(
    (SELECT expected_salary_max FROM user_profile WHERE user_id = @default_user_id LIMIT 1),
    15000
);
SET @default_industry_name := (
    SELECT industry_name
    FROM biz_job_posting
    WHERE city_code = @default_city_code
      AND industry_name IS NOT NULL
      AND industry_name <> ''
    GROUP BY industry_name
    ORDER BY COUNT(*) DESC
    LIMIT 1
);

INSERT INTO user_report_preference (
    user_id,
    preference_name,
    major_id,
    region_code,
    province_code,
    city_code,
    salary_min,
    salary_max,
    job_category_id,
    company_name_keyword,
    company_industry,
    company_size,
    company_finance,
    sort_type,
    is_default,
    created_at,
    updated_at
)
SELECT
    @default_user_id,
    '默认岗位偏好',
    @default_major_id,
    @default_region_code,
    @default_province_code,
    @default_city_code,
    @default_salary_min,
    @default_salary_max,
    @default_job_category_id,
    NULL,
    @default_industry_name,
    NULL,
    NULL,
    'salary_desc',
    1,
    NOW(),
    NOW()
FROM DUAL
WHERE @default_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_report_preference WHERE user_id = @default_user_id
  );

SET @default_preference_id := (
    SELECT id FROM user_report_preference
    WHERE user_id = @default_user_id
    ORDER BY is_default DESC, id ASC
    LIMIT 1
);

INSERT INTO biz_report_snapshot (
    report_id,
    task_id,
    user_id,
    filter_snapshot,
    data_snapshot,
    data_start_time,
    data_end_time,
    created_at
)
SELECT
    r.id,
    r.task_id,
    COALESCE(r.generated_by, t.created_by, @default_user_id),
    COALESCE(
        t.params,
        JSON_OBJECT(
            'userId', COALESCE(r.generated_by, t.created_by, @default_user_id),
            'preferenceId', @default_preference_id,
            'cityCode', @default_city_code,
            'jobCategoryId', @default_job_category_id
        )
    ),
    r.analysis_data,
    DATE_SUB(r.generated_at, INTERVAL 30 DAY),
    r.generated_at,
    NOW()
FROM biz_analysis_report r
LEFT JOIN biz_analysis_task t ON t.id = r.task_id
LEFT JOIN biz_report_snapshot s ON s.report_id = r.id
WHERE s.id IS NULL;

SET @default_report_id := (
    SELECT id
    FROM biz_analysis_report
    WHERE COALESCE(generated_by, @default_user_id) = @default_user_id
    ORDER BY generated_at DESC, id DESC
    LIMIT 1
);

INSERT INTO biz_recommendation_result (
    user_id,
    report_id,
    preference_id,
    job_posting_id,
    match_score,
    match_reason,
    rank_no,
    is_viewed,
    created_at
)
SELECT
    @default_user_id,
    @default_report_id,
    @default_preference_id,
    jp.id,
    ROUND(
        (CASE WHEN jp.city_code = @default_city_code THEN 40 ELSE 0 END) +
        (CASE WHEN jp.job_category_id = @default_job_category_id THEN 40 ELSE 0 END) +
        (CASE
            WHEN jp.salary_max IS NOT NULL AND jp.salary_max >= @default_salary_min THEN 10
            ELSE 0
         END) +
        (CASE
            WHEN jp.industry_name = @default_industry_name THEN 10
            ELSE 0
         END),
        2
    ) AS match_score,
    CONCAT(
        '城市匹配:',
        CASE WHEN jp.city_code = @default_city_code THEN '是' ELSE '否' END,
        '；岗位匹配:',
        CASE WHEN jp.job_category_id = @default_job_category_id THEN '是' ELSE '否' END,
        '；行业参考:',
        COALESCE(jp.industry_name, '未识别')
    ) AS match_reason,
    ranked.rank_no,
    0,
    NOW()
FROM (
    SELECT
        jp.id,
        ROW_NUMBER() OVER (
            ORDER BY
                (jp.city_code = @default_city_code) DESC,
                (jp.job_category_id = @default_job_category_id) DESC,
                jp.salary_max DESC,
                jp.publish_date DESC,
                jp.id DESC
        ) AS rank_no
    FROM biz_job_posting jp
    WHERE jp.is_active = 1
      AND jp.city_code IS NOT NULL
      AND jp.job_category_id IS NOT NULL
      AND (@default_city_code IS NULL OR jp.city_code = @default_city_code OR jp.province_code = @default_province_code)
      AND (@default_job_category_id IS NULL OR jp.job_category_id = @default_job_category_id)
    LIMIT 20
) ranked
JOIN biz_job_posting jp ON jp.id = ranked.id
LEFT JOIN biz_recommendation_result rr
       ON rr.user_id = @default_user_id
      AND rr.preference_id <=> @default_preference_id
      AND rr.job_posting_id = jp.id
WHERE @default_user_id IS NOT NULL
  AND @default_preference_id IS NOT NULL
  AND rr.id IS NULL;

SELECT
    (SELECT COUNT(*) FROM user_report_preference) AS preference_count,
    (SELECT COUNT(*) FROM biz_report_snapshot) AS report_snapshot_count,
    (SELECT COUNT(*) FROM biz_recommendation_result) AS recommendation_count;
