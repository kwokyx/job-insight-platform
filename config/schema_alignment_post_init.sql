USE career_platform;

CREATE TABLE IF NOT EXISTS `dim_industry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `industry_code` varchar(50) NOT NULL COMMENT 'Industry Code',
  `industry_name` varchar(100) NOT NULL COMMENT 'Industry Name',
  `parent_id` bigint DEFAULT NULL COMMENT 'Parent ID',
  `industry_level` tinyint NOT NULL DEFAULT '1' COMMENT 'Industry Level',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dim_industry_code` (`industry_code`),
  KEY `idx_dim_industry_name` (`industry_name`),
  KEY `idx_dim_industry_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dimension Industry table';

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'job_id_source'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `job_id_source` varchar(100) DEFAULT NULL COMMENT ''Source Job ID'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'company_id'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `company_id` bigint DEFAULT NULL COMMENT ''Company ID'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'city'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `city` varchar(100) DEFAULT NULL COMMENT ''City'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'region'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `region` varchar(100) DEFAULT NULL COMMENT ''Region'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'district'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `district` varchar(100) DEFAULT NULL COMMENT ''District'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'industry_id'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `industry_id` bigint DEFAULT NULL COMMENT ''Industry ID'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'industry_name'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `industry_name` varchar(100) DEFAULT NULL COMMENT ''Industry Name'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'education'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `education` varchar(50) DEFAULT NULL COMMENT ''Education'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'experience'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `experience` varchar(50) DEFAULT NULL COMMENT ''Experience'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'salary_unit'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `salary_unit` varchar(20) DEFAULT NULL COMMENT ''Salary Unit'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'salary_text'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `salary_text` varchar(100) DEFAULT NULL COMMENT ''Salary Text'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'employment_type'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `employment_type` varchar(50) DEFAULT NULL COMMENT ''Employment Type'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'job_benefits'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `job_benefits` json DEFAULT NULL COMMENT ''Job Benefits'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'description'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `description` text COMMENT ''Description'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'requirements'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `requirements` text COMMENT ''Requirements'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'source_site'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `source_site` varchar(100) DEFAULT NULL COMMENT ''Source Site'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'source_url'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `source_url` varchar(500) DEFAULT NULL COMMENT ''Source URL'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'data_quality'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `data_quality` tinyint DEFAULT NULL COMMENT ''Data Quality'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'is_active'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `is_active` tinyint DEFAULT ''1'' COMMENT ''Is Active'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'created_at'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''Created At'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'biz_job_posting' AND COLUMN_NAME = 'updated_at'),
  'SELECT 1',
  'ALTER TABLE `biz_job_posting` ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''Updated At'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `biz_job_posting`
SET
  `job_id_source` = COALESCE(`job_id_source`, `url_obj_id`),
  `city` = COALESCE(`city`, `job_city`),
  `industry_name` = COALESCE(`industry_name`, `job_classification`),
  `education` = COALESCE(`education`, `education_need`),
  `experience` = COALESCE(`experience`, `experience_year`),
  `salary_text` = COALESCE(`salary_text`, `salary_raw`),
  `salary_unit` = COALESCE(`salary_unit`, 'month'),
  `description` = COALESCE(`description`, `position_info`),
  `requirements` = COALESCE(`requirements`, `position_info`),
  `source_url` = COALESCE(`source_url`, `url`),
  `source_site` = COALESCE(
      `source_site`,
      CASE
        WHEN COALESCE(`source_url`, `url`, '') LIKE '%zhaopin%' THEN 'zhaopin'
        WHEN COALESCE(`source_url`, `url`, '') LIKE '%51job%' THEN '51job'
        WHEN COALESCE(`source_url`, `url`, '') LIKE '%zhipin%' THEN 'boss'
        ELSE 'unknown'
      END
  ),
  `job_benefits` = COALESCE(
      `job_benefits`,
      CASE
        WHEN `job_welfare` IS NULL OR TRIM(`job_welfare`) = '' THEN JSON_ARRAY()
        WHEN JSON_VALID(`job_welfare`) THEN JSON_EXTRACT(`job_welfare`, '$')
        ELSE JSON_ARRAY(`job_welfare`)
      END
  ),
  `data_quality` = COALESCE(`data_quality`, 3),
  `is_active` = COALESCE(`is_active`, 1),
  `created_at` = COALESCE(`created_at`, `crawl_time`, NOW()),
  `updated_at` = COALESCE(`updated_at`, `crawl_update_time`, `crawl_time`, NOW());

UPDATE `biz_job_posting` jp
JOIN `biz_company` bc
  ON bc.company_name = jp.company_name
SET jp.company_id = COALESCE(jp.company_id, bc.id)
WHERE jp.company_name IS NOT NULL
  AND jp.company_name <> '';

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'target_job'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `target_job` varchar(100) DEFAULT NULL COMMENT ''Target Job'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'current_job'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `current_job` varchar(100) DEFAULT NULL COMMENT ''Current Job'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'target_city_name'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `target_city_name` varchar(100) DEFAULT NULL COMMENT ''Target City Name'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'industry'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `industry` varchar(100) DEFAULT NULL COMMENT ''Industry'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'experience_years'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `experience_years` int DEFAULT NULL COMMENT ''Experience Years'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'resume_text'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `resume_text` text COMMENT ''Resume Text'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @stmt = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'career_platform' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'resume_file_name'),
  'SELECT 1',
  'ALTER TABLE `user_profile` ADD COLUMN `resume_file_name` varchar(255) DEFAULT NULL COMMENT ''Resume File Name'''
);
PREPARE stmt FROM @stmt; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `biz_webhook_delivery`;
DROP TABLE IF EXISTS `biz_webhook_endpoint`;
