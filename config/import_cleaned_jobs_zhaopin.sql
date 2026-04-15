USE career_platform;

INSERT INTO crawl_task (task_id, task_name, channel, status, priority, create_user)
VALUES ('1', 'zhaopin csv import', 'zhaopin', 2, 5, 'codex')
ON DUPLICATE KEY UPDATE
    task_name = VALUES(task_name),
    channel = VALUES(channel),
    update_time = CURRENT_TIMESTAMP;

TRUNCATE TABLE crawl_job_posting;

LOAD DATA INFILE '/var/lib/mysql-files/cleaned_jobs_zhaopin.csv'
INTO TABLE crawl_job_posting
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(@url,@url_obj_id,@title,@salary_min,@salary_max,@salary_raw,@job_city,@experience_year,@education_need,@publish_date,@job_welfare,@job_labels,@position_info,@job_classification,@company_name,@company_size,@company_finance,@crawl_time,@crawl_update_time,@company_logo,@task_id)
SET
  url = NULLIF(@url,''),
  url_obj_id = NULLIF(@url_obj_id,''),
  title = NULLIF(@title,''),
  salary_min = NULLIF(@salary_min,''),
  salary_max = NULLIF(@salary_max,''),
  salary_raw = NULLIF(@salary_raw,''),
  job_city = NULLIF(@job_city,''),
  experience_year = NULLIF(@experience_year,''),
  education_need = NULLIF(@education_need,''),
  publish_date = IF(@publish_date='', NULL, STR_TO_DATE(@publish_date, '%Y-%m-%d %H:%i:%s')),
  job_welfare = NULLIF(@job_welfare,''),
  job_labels = NULLIF(@job_labels,''),
  position_info = NULLIF(@position_info,''),
  job_classification = NULLIF(@job_classification,''),
  company_name = NULLIF(@company_name,''),
  company_size = NULLIF(@company_size,''),
  company_finance = NULLIF(@company_finance,''),
  crawl_time = IF(@crawl_time='', NULL, COALESCE(STR_TO_DATE(@crawl_time, '%e/%c/%Y %H:%i:%s'), STR_TO_DATE(@crawl_time, '%Y-%m-%d %H:%i:%s'))),
  crawl_update_time = IF(@crawl_update_time='', NULL, COALESCE(STR_TO_DATE(@crawl_update_time, '%e/%c/%Y %H:%i:%s'), STR_TO_DATE(@crawl_update_time, '%Y-%m-%d %H:%i:%s'))),
  company_logo = NULLIF(@company_logo,''),
  task_id = NULLIF(TRIM(REPLACE(@task_id, '\r', '')),'');

SELECT COUNT(*) AS imported_rows FROM crawl_job_posting;
