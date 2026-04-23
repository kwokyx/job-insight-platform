USE career_platform;


DROP TABLE IF EXISTS `ads_dashboard_kpi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ads_dashboard_kpi` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `stat_date` date NOT NULL COMMENT 'Stat Date',
  `total_jobs` bigint DEFAULT NULL COMMENT 'Total Jobs',
  `active_jobs` bigint DEFAULT NULL COMMENT 'Active Jobs',
  `total_companies` bigint DEFAULT NULL COMMENT 'Total Companies',
  `avg_salary` decimal(10,2) DEFAULT NULL COMMENT 'Average Salary',
  `median_salary` decimal(10,2) DEFAULT NULL COMMENT 'Median Salary',
  `top_city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Top City',
  `top_industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Top Industry',
  `top_skill` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Top Skill',
  `new_jobs_7d` int DEFAULT NULL COMMENT 'New Jobs 7D',
  `salary_trend` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Salary Trend',
  `etl_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL Time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ADS Dashboard KPI table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ads_page_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ads_page_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `page_code` varchar(64) NOT NULL COMMENT 'Snapshot code',
  `page_name` varchar(100) NOT NULL COMMENT 'Snapshot name',
  `payload_json` json NOT NULL COMMENT 'Snapshot payload',
  `refresh_trigger` varchar(64) DEFAULT NULL COMMENT 'Refresh trigger',
  `refreshed_at` datetime DEFAULT NULL COMMENT 'Refreshed at',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ads_page_snapshot_code` (`page_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Page-level snapshot cache';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `agent_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `task_type` varchar(50) NOT NULL,
  `task_title` varchar(200) NOT NULL,
  `task_goal` text,
  `input_payload` json DEFAULT NULL,
  `status` varchar(20) NOT NULL COMMENT 'pending/running/success/failed/cancelled',
  `priority` int DEFAULT '5',
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_task_user` (`user_id`),
  KEY `idx_agent_task_conversation` (`conversation_id`),
  KEY `idx_agent_task_status` (`status`),
  CONSTRAINT `fk_agent_task_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_agent_task_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent task table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `agent_task_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_task_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `step_no` int NOT NULL,
  `step_name` varchar(200) NOT NULL,
  `step_type` varchar(50) NOT NULL COMMENT 'plan/search/query/tool/summary/output',
  `input_data` json DEFAULT NULL,
  `output_data` json DEFAULT NULL,
  `status` varchar(20) NOT NULL COMMENT 'pending/running/success/failed/skipped',
  `error_message` varchar(500) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_task_step` (`task_id`,`step_no`),
  CONSTRAINT `fk_agent_task_step_task` FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent task step table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `agent_tool_call`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_tool_call` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `step_id` bigint DEFAULT NULL,
  `tool_name` varchar(100) NOT NULL,
  `tool_type` varchar(50) DEFAULT NULL,
  `request_payload` json DEFAULT NULL,
  `response_payload` json DEFAULT NULL,
  `status` varchar(20) NOT NULL COMMENT 'success/failed/timeout',
  `latency_ms` int DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_tool_call_task` (`task_id`),
  KEY `idx_agent_tool_call_step` (`step_id`),
  CONSTRAINT `fk_agent_tool_call_step` FOREIGN KEY (`step_id`) REFERENCES `agent_task_step` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_agent_tool_call_task` FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent tool call table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ai_artifact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `conversation_id` bigint DEFAULT NULL,
  `artifact_type` varchar(50) NOT NULL COMMENT 'report/json/markdown/sql/chart/file',
  `artifact_name` varchar(200) NOT NULL,
  `content_text` longtext,
  `content_json` json DEFAULT NULL,
  `file_url` varchar(500) DEFAULT NULL,
  `version_no` int DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_artifact_task` (`task_id`),
  KEY `idx_ai_artifact_conversation` (`conversation_id`),
  CONSTRAINT `fk_ai_artifact_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_artifact_task` FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI artifact table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ai_context_memory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_context_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `conversation_id` bigint DEFAULT NULL,
  `memory_type` varchar(50) NOT NULL COMMENT 'profile/preference/summary/task_context',
  `memory_key` varchar(100) NOT NULL,
  `memory_value` text NOT NULL,
  `source_type` varchar(50) DEFAULT NULL,
  `importance_score` decimal(5,2) DEFAULT NULL,
  `expired_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_memory_user` (`user_id`),
  KEY `idx_ai_memory_conversation` (`conversation_id`),
  KEY `idx_ai_memory_type_key` (`memory_type`,`memory_key`),
  CONSTRAINT `fk_ai_memory_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_memory_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI context memory table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ai_conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Session ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Title',
  `context_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Context Type',
  `status` tinyint DEFAULT '1' COMMENT 'Status',
  `message_count` int DEFAULT '0' COMMENT 'Message Count',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_id` (`session_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_session` (`session_id`),
  CONSTRAINT `ai_conversation_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Conversation table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ai_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint DEFAULT NULL,
  `message_id` bigint DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `feedback_type` varchar(50) NOT NULL COMMENT 'like/dislike/rating/correction',
  `rating_score` int DEFAULT NULL,
  `feedback_text` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_feedback_user` (`user_id`),
  KEY `idx_ai_feedback_conversation` (`conversation_id`),
  KEY `idx_ai_feedback_message` (`message_id`),
  KEY `idx_ai_feedback_task` (`task_id`),
  CONSTRAINT `fk_ai_feedback_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_feedback_message` FOREIGN KEY (`message_id`) REFERENCES `ai_message` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_feedback_task` FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI feedback table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ai_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `conversation_id` bigint NOT NULL COMMENT 'Conversation ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Role',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Content',
  `content_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'text' COMMENT 'Content Type',
  `metadata` json DEFAULT NULL COMMENT 'Metadata',
  `tokens_used` int DEFAULT NULL COMMENT 'Tokens Used',
  `latency_ms` int DEFAULT NULL COMMENT 'Latency Ms',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`),
  CONSTRAINT `ai_message_ibfk_1` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=84 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Message table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_analysis_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_analysis_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint DEFAULT NULL COMMENT 'Task ID',
  `report_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Report Name',
  `report_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Report Type',
  `report_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'HTML' COMMENT 'Report Format',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Description',
  `analysis_data` json DEFAULT NULL COMMENT 'Analysis Data',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'File URL',
  `file_size` bigint DEFAULT NULL COMMENT 'File Size',
  `is_public` tinyint DEFAULT '0' COMMENT 'Is Public',
  `view_count` int DEFAULT '0' COMMENT 'View Count',
  `download_count` int DEFAULT '0' COMMENT 'Download Count',
  `generated_by` bigint DEFAULT NULL COMMENT 'Generated By',
  `generated_at` datetime NOT NULL COMMENT 'Generated At',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`report_type`),
  KEY `idx_public` (`is_public`),
  KEY `idx_generated` (`generated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Analysis Report table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_analysis_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_analysis_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Task Name',
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Task Type',
  `params` json DEFAULT NULL COMMENT 'Parameters',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'PENDING' COMMENT 'Status',
  `progress` tinyint DEFAULT '0' COMMENT 'Progress',
  `result_summary` json DEFAULT NULL COMMENT 'Result Summary',
  `result_file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Result File URL',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Error Message',
  `created_by` bigint DEFAULT NULL COMMENT 'Created By',
  `started_at` datetime DEFAULT NULL COMMENT 'Started At',
  `completed_at` datetime DEFAULT NULL COMMENT 'Completed At',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`task_type`),
  KEY `idx_status` (`status`),
  KEY `idx_creator` (`created_by`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Analysis Task table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_career_path`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_career_path` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_title_from` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Job Title From',
  `job_title_to` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Job Title To',
  `transition_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Transition Type',
  `avg_years` decimal(3,1) DEFAULT NULL COMMENT 'Average Years',
  `required_skills` json DEFAULT NULL COMMENT 'Required Skills',
  `frequency` int DEFAULT '0' COMMENT 'Frequency',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_from` (`job_title_from`),
  KEY `idx_to` (`job_title_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Career Path table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_company` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Company Name',
  `short_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Short Name',
  `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Industry',
  `company_size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Size',
  `company_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Type',
  `company_finance` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Finance',
  `region_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Region Code',
  `province_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Province Code',
  `city_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'City Code',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Address',
  `website` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Website',
  `logo_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Logo URL',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Description',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  KEY `idx_industry` (`industry`),
  KEY `idx_name` (`company_name`),
  KEY `idx_biz_company_region_code` (`region_code`),
  KEY `idx_biz_company_province_code` (`province_code`),
  KEY `idx_biz_company_city_code` (`city_code`),
  CONSTRAINT `fk_biz_company_city_code` FOREIGN KEY (`city_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_biz_company_province_code` FOREIGN KEY (`province_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_biz_company_region_code` FOREIGN KEY (`region_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=72533 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Company table';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_curriculum`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_curriculum` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `course_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Course Name',
  `course_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Course Code',
  `department` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Department',
  `major` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Major',
  `credit` decimal(3,1) DEFAULT NULL COMMENT 'Credit',
  `semester` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Semester',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Description',
  `keywords` json DEFAULT NULL COMMENT 'Keywords',
  `is_active` tinyint DEFAULT '1' COMMENT 'Is Active',
  `uploaded_by` bigint DEFAULT NULL COMMENT 'Uploaded By',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  KEY `idx_major` (`major`),
  KEY `idx_dept` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Curriculum table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_curriculum_skill_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_curriculum_skill_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `curriculum_id` bigint NOT NULL COMMENT 'Curriculum ID',
  `skill_id` bigint NOT NULL COMMENT 'Skill ID',
  `relevance` decimal(3,2) DEFAULT '1.00' COMMENT 'Relevance',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'AUTO' COMMENT 'Source',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_curriculum_skill` (`curriculum_id`,`skill_id`),
  KEY `fk_curriculum_skill_mapping_label` (`skill_id`),
  CONSTRAINT `biz_curriculum_skill_mapping_ibfk_1` FOREIGN KEY (`curriculum_id`) REFERENCES `biz_curriculum` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_curriculum_skill_mapping_label` FOREIGN KEY (`skill_id`) REFERENCES `job_label_dict` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Curriculum Skill Mapping table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_data_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `source_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Source Name',
  `source_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Source Code',
  `base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Base URL',
  `crawl_strategy` json DEFAULT NULL COMMENT 'Crawl Strategy',
  `is_active` tinyint DEFAULT '1' COMMENT 'Is Active',
  `last_crawl_at` datetime DEFAULT NULL COMMENT 'Last Crawl At',
  `total_records` bigint DEFAULT '0' COMMENT 'Total Records',
  `health_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'UNKNOWN' COMMENT 'Health Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `source_code` (`source_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Data Source table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_employment_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_employment_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `indicator_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Indicator Name',
  `indicator_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Indicator Code',
  `metric_scope` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Metric Scope',
  `period` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Period',
  `region` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Region',
  `value` decimal(12,4) DEFAULT NULL COMMENT 'Value',
  `unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Unit',
  `source_site` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Source Site',
  `source_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Source URL',
  `publish_date` date DEFAULT NULL COMMENT 'Publish Date',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_indicator` (`indicator_code`),
  KEY `idx_period` (`period`),
  KEY `idx_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Employment Indicator table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_job_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_job_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_id` bigint NOT NULL COMMENT 'Job ID',
  `snapshot_data` json NOT NULL COMMENT 'Snapshot Data',
  `salary_min` decimal(10,2) DEFAULT NULL COMMENT 'Salary Minimum',
  `salary_max` decimal(10,2) DEFAULT NULL COMMENT 'Salary Maximum',
  `is_active` tinyint DEFAULT NULL COMMENT 'Is Active',
  `change_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Change Type',
  `crawl_time` datetime NOT NULL COMMENT 'Crawl Time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_job` (`job_id`),
  KEY `idx_time` (`crawl_time`),
  CONSTRAINT `biz_job_history_ibfk_1` FOREIGN KEY (`job_id`) REFERENCES `biz_job_posting` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Job History table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_job_posting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_job_posting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `url_obj_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'URL Object ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Title',
  `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Company Name',
  `company_size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Size',
  `company_finance` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Finance',
  `region_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Region Code',
  `province_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Province Code',
  `city_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'City Code',
  `job_city` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Job City',
  `job_classification` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Job Classification',
  `job_category_id` bigint DEFAULT NULL COMMENT 'Job Category ID',
  `education_need` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Education Need',
  `experience_year` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Experience Year',
  `salary_min` decimal(10,2) DEFAULT NULL COMMENT 'Salary Minimum',
  `salary_max` decimal(10,2) DEFAULT NULL COMMENT 'Salary Maximum',
  `salary_raw` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Salary Raw',
  `job_welfare` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Job Welfare',
  `job_labels` json DEFAULT NULL COMMENT 'Job Labels',
  `position_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Position Info',
  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'URL',
  `publish_date` date DEFAULT NULL COMMENT 'Publish Date',
  `crawl_time` datetime NOT NULL COMMENT 'Crawl Time',
  `crawl_update_time` datetime DEFAULT NULL COMMENT 'Crawl Update Time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_job` (`url_obj_id`),
  KEY `idx_title` (`title`),
  KEY `idx_city` (`job_city`),
  KEY `idx_education` (`education_need`),
  KEY `idx_salary` (`salary_min`,`salary_max`),
  KEY `idx_publish_date` (`publish_date`),
  KEY `idx_crawl_time` (`crawl_time`),
  KEY `idx_biz_job_posting_region_code` (`region_code`),
  KEY `idx_biz_job_posting_province_code` (`province_code`),
  KEY `idx_biz_job_posting_city_code` (`city_code`),
  KEY `idx_biz_job_posting_category_id` (`job_category_id`),
  FULLTEXT KEY `ft_description` (`position_info`),
  CONSTRAINT `fk_biz_job_posting_city_code` FOREIGN KEY (`city_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_biz_job_posting_job_category` FOREIGN KEY (`job_category_id`) REFERENCES `dim_job_category` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_biz_job_posting_province_code` FOREIGN KEY (`province_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_biz_job_posting_region_code` FOREIGN KEY (`region_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=264391 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Job Posting table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Title',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Content',
  `notify_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Notify Type',
  `ref_id` bigint DEFAULT NULL COMMENT 'Ref ID',
  `is_read` tinyint DEFAULT '0' COMMENT 'Is Read',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_read` (`is_read`),
  KEY `idx_time` (`created_at`),
  CONSTRAINT `biz_notification_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Notification table';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_recommendation_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_recommendation_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `report_id` bigint DEFAULT NULL,
  `preference_id` bigint DEFAULT NULL,
  `job_posting_id` bigint NOT NULL,
  `match_score` decimal(5,2) NOT NULL,
  `match_reason` varchar(500) DEFAULT NULL,
  `rank_no` int DEFAULT NULL,
  `is_viewed` tinyint NOT NULL DEFAULT '0' COMMENT '0-unviewed 1-viewed',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recommendation_user` (`user_id`),
  KEY `idx_recommendation_report` (`report_id`),
  KEY `idx_recommendation_preference` (`preference_id`),
  KEY `idx_recommendation_job` (`job_posting_id`),
  CONSTRAINT `fk_recommendation_job` FOREIGN KEY (`job_posting_id`) REFERENCES `biz_job_posting` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_recommendation_preference` FOREIGN KEY (`preference_id`) REFERENCES `user_report_preference` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_recommendation_report` FOREIGN KEY (`report_id`) REFERENCES `biz_analysis_report` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_recommendation_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recommendation result table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_report_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_report_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `schedule_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Schedule Name',
  `report_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Report Type',
  `cron_expr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Cron Expression',
  `params` json DEFAULT NULL COMMENT 'Parameters',
  `is_active` tinyint DEFAULT '1' COMMENT 'Is Active',
  `last_run_at` datetime DEFAULT NULL COMMENT 'Last Run At',
  `next_run_at` datetime DEFAULT NULL COMMENT 'Next Run At',
  `created_by` bigint DEFAULT NULL COMMENT 'Created By',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Report Schedule table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_report_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_report_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `report_id` bigint NOT NULL COMMENT 'Report ID',
  `task_id` bigint DEFAULT NULL COMMENT 'Task ID',
  `user_id` bigint DEFAULT NULL COMMENT 'User ID',
  `filter_snapshot` json NOT NULL COMMENT 'Filter Snapshot',
  `data_snapshot` json DEFAULT NULL COMMENT 'Data Snapshot',
  `data_start_time` datetime DEFAULT NULL COMMENT 'Data Start Time',
  `data_end_time` datetime DEFAULT NULL COMMENT 'Data End Time',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_report_snapshot_report` (`report_id`),
  KEY `idx_report_snapshot_task` (`task_id`),
  KEY `idx_report_snapshot_user` (`user_id`),
  CONSTRAINT `fk_report_snapshot_report` FOREIGN KEY (`report_id`) REFERENCES `biz_analysis_report` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_snapshot_task` FOREIGN KEY (`task_id`) REFERENCES `biz_analysis_task` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_report_snapshot_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Report Snapshot table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_skill_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_skill_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `skill_id_a` bigint NOT NULL COMMENT 'Skill ID A',
  `skill_id_b` bigint NOT NULL COMMENT 'Skill ID B',
  `relation_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Relation Type',
  `weight` decimal(5,2) DEFAULT '1.00' COMMENT 'Weight',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_pair` (`skill_id_a`,`skill_id_b`,`relation_type`),
  KEY `fk_skill_relation_label_b` (`skill_id_b`),
  CONSTRAINT `fk_skill_relation_label_a` FOREIGN KEY (`skill_id_a`) REFERENCES `job_label_dict` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_skill_relation_label_b` FOREIGN KEY (`skill_id_b`) REFERENCES `job_label_dict` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Skill Relation table';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_user_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `subscription_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Subscription Type',
  `filter_config` json DEFAULT NULL COMMENT 'Filter Config',
  `channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'IN_APP' COMMENT 'Channel',
  `is_active` tinyint DEFAULT '1' COMMENT 'Is Active',
  `last_pushed_at` datetime DEFAULT NULL COMMENT 'Last Pushed At',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_type` (`subscription_type`),
  CONSTRAINT `biz_user_subscription_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business User Subscription table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_webhook_delivery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_webhook_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `endpoint_id` bigint NOT NULL COMMENT 'Endpoint ID',
  `event_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Event Type',
  `payload` json NOT NULL COMMENT 'Payload',
  `http_status` int DEFAULT NULL COMMENT 'HTTP Status',
  `response_body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Response Body',
  `response_time` int DEFAULT NULL COMMENT 'Response Time',
  `attempt` int DEFAULT '1' COMMENT 'Attempt',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'PENDING' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_endpoint` (`endpoint_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `biz_webhook_delivery_ibfk_1` FOREIGN KEY (`endpoint_id`) REFERENCES `biz_webhook_endpoint` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Webhook Delivery table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_webhook_endpoint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_webhook_endpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `endpoint_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Endpoint URL',
  `secret_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Secret Key',
  `event_types` json DEFAULT NULL COMMENT 'Event Types',
  `is_active` tinyint DEFAULT '1' COMMENT 'Is Active',
  `last_triggered` datetime DEFAULT NULL COMMENT 'Last Triggered',
  `fail_count` int DEFAULT '0' COMMENT 'Fail Count',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `biz_webhook_endpoint_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Business Webhook Endpoint table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crawl_job_posting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_job_posting` (
  `url` varchar(500) DEFAULT NULL,
  `url_obj_id` varchar(100) NOT NULL,
  `title` varchar(200) NOT NULL,
  `salary_min` int DEFAULT '0',
  `salary_max` int DEFAULT '0',
  `salary_raw` varchar(50) DEFAULT NULL,
  `job_city` varchar(50) NOT NULL,
  `experience_year` varchar(50) DEFAULT NULL,
  `education_need` varchar(50) DEFAULT NULL,
  `publish_date` datetime DEFAULT NULL,
  `job_welfare` text,
  `job_labels` text,
  `position_info` text,
  `job_classification` varchar(100) DEFAULT NULL,
  `company_name` varchar(100) DEFAULT NULL,
  `company_size` varchar(50) DEFAULT NULL,
  `company_finance` varchar(50) DEFAULT NULL,
  `crawl_time` datetime NOT NULL,
  `crawl_update_time` datetime DEFAULT NULL,
  `company_logo` varchar(500) DEFAULT NULL,
  `task_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`url_obj_id`),
  KEY `idx_crawl_job_task` (`task_id`),
  KEY `idx_crawl_job_city` (`job_city`),
  CONSTRAINT `fk_crawl_job_task` FOREIGN KEY (`task_id`) REFERENCES `crawl_task` (`task_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Distributed crawl job table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crawl_proxy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_proxy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `proxy_ip` varchar(50) NOT NULL,
  `protocol` varchar(10) NOT NULL DEFAULT 'http',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0-unavailable 1-available',
  `fail_count` int DEFAULT '0',
  `success_rate` float DEFAULT '100',
  `avg_response_time` float DEFAULT '0',
  `last_used_time` datetime DEFAULT NULL,
  `worker_id` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_crawl_proxy_worker` (`worker_id`),
  CONSTRAINT `fk_crawl_proxy_worker` FOREIGN KEY (`worker_id`) REFERENCES `crawl_worker` (`worker_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Crawl proxy table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crawl_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_task` (
  `task_id` varchar(64) NOT NULL,
  `parent_task_id` varchar(64) DEFAULT NULL,
  `task_name` varchar(100) NOT NULL,
  `channel` varchar(32) NOT NULL,
  `keywords` text,
  `city` varchar(100) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-pending 1-running 2-finished 3-failed',
  `priority` int NOT NULL DEFAULT '5',
  `total_count` int DEFAULT '0',
  `finished_count` int DEFAULT '0',
  `duplicate_count` int DEFAULT '0',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `create_user` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`task_id`),
  KEY `idx_crawl_task_parent` (`parent_task_id`),
  KEY `idx_crawl_task_channel` (`channel`),
  KEY `idx_crawl_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Crawl task table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crawl_task_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_task_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `shard_id` varchar(64) DEFAULT NULL,
  `worker_id` varchar(64) DEFAULT NULL,
  `level` varchar(10) NOT NULL DEFAULT 'INFO',
  `message` text NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `idx_crawl_task_log_task` (`task_id`),
  KEY `idx_crawl_task_log_shard` (`shard_id`),
  KEY `idx_crawl_task_log_worker` (`worker_id`),
  CONSTRAINT `fk_crawl_task_log_shard` FOREIGN KEY (`shard_id`) REFERENCES `crawl_task_shard` (`shard_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_crawl_task_log_task` FOREIGN KEY (`task_id`) REFERENCES `crawl_task` (`task_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crawl_task_log_worker` FOREIGN KEY (`worker_id`) REFERENCES `crawl_worker` (`worker_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Crawl task log table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crawl_task_shard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_task_shard` (
  `shard_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `page` int NOT NULL,
  `keyword` varchar(100) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `category_code` varchar(50) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-pending 1-running 2-finished 3-failed',
  `retry_count` int NOT NULL DEFAULT '0',
  `worker_id` varchar(64) DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  PRIMARY KEY (`shard_id`),
  KEY `idx_shard_task` (`task_id`),
  KEY `idx_shard_worker` (`worker_id`),
  CONSTRAINT `fk_crawl_task_shard_task` FOREIGN KEY (`task_id`) REFERENCES `crawl_task` (`task_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crawl_task_shard_worker` FOREIGN KEY (`worker_id`) REFERENCES `crawl_worker` (`worker_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Crawl task shard table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `crawl_worker`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crawl_worker` (
  `worker_id` varchar(64) NOT NULL,
  `ip` varchar(50) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0-offline 1-online',
  `current_task_id` varchar(64) DEFAULT NULL,
  `cpu_usage` float DEFAULT '0',
  `memory_usage` float DEFAULT '0',
  `last_heartbeat` datetime NOT NULL,
  PRIMARY KEY (`worker_id`),
  KEY `idx_crawl_worker_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Crawl worker table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dim_industry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dim_industry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `industry_code` varchar(50) NOT NULL,
  `industry_name` varchar(100) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `industry_level` tinyint NOT NULL COMMENT '1-level 2-level',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-enabled 0-disabled',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dim_industry_code` (`industry_code`),
  KEY `idx_dim_industry_parent` (`parent_id`),
  CONSTRAINT `fk_dim_industry_parent` FOREIGN KEY (`parent_id`) REFERENCES `dim_industry` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Industry dimension table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dim_job_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dim_job_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Category Code',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Category Name',
  `parent_id` bigint DEFAULT NULL COMMENT 'Parent ID',
  `category_level` tinyint NOT NULL COMMENT 'Category Level',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Description',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_category_name` (`category_name`),
  CONSTRAINT `fk_dim_job_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `dim_job_category` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1131 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dimension Job Category table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dim_major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dim_major` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `major_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Major Code',
  `major_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Major Name',
  `major_category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Major Category',
  `education_level` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Education Level',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_code` (`major_code`),
  KEY `idx_major_name` (`major_name`),
  KEY `idx_major_category` (`major_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dimension Major table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dim_region`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dim_region` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `region_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Region Code',
  `region_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Region Name',
  `region_level` tinyint NOT NULL COMMENT 'Region Level',
  `parent_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Parent Code',
  `full_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Full Name',
  `sort_no` int DEFAULT '0' COMMENT 'Sort No',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_region_code` (`region_code`),
  KEY `idx_parent_code` (`parent_code`),
  KEY `idx_region_level` (`region_level`),
  CONSTRAINT `fk_dim_region_parent_code` FOREIGN KEY (`parent_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=431 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dimension Region table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dwd_job_fact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dwd_job_fact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_id` bigint NOT NULL COMMENT 'Job ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Title',
  `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Name',
  `city_std` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'City Standard',
  `province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Province',
  `industry_std` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Industry Standard',
  `industry_l1` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Industry Level 1',
  `education_std` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Education Standard',
  `experience_min` int DEFAULT NULL COMMENT 'Experience Minimum',
  `experience_max` int DEFAULT NULL COMMENT 'Experience Maximum',
  `salary_min` decimal(10,2) DEFAULT NULL COMMENT 'Salary Minimum',
  `salary_max` decimal(10,2) DEFAULT NULL COMMENT 'Salary Maximum',
  `salary_avg` decimal(10,2) DEFAULT NULL COMMENT 'Salary Average',
  `skill_tags` json DEFAULT NULL COMMENT 'Skill Tags',
  `publish_date` date DEFAULT NULL COMMENT 'Publish Date',
  `source_site` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Source Site',
  `etl_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL Time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job` (`job_id`),
  KEY `idx_city` (`city_std`),
  KEY `idx_industry` (`industry_std`),
  KEY `idx_education` (`education_std`),
  KEY `idx_publish` (`publish_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DWD Job Fact table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dws_daily_city_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dws_daily_city_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `stat_date` date NOT NULL COMMENT 'Stat Date',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'City',
  `job_count` int DEFAULT '0' COMMENT 'Job Count',
  `avg_salary_min` decimal(10,2) DEFAULT NULL COMMENT 'Average Salary Minimum',
  `avg_salary_max` decimal(10,2) DEFAULT NULL COMMENT 'Average Salary Maximum',
  `new_jobs_count` int DEFAULT '0' COMMENT 'New Jobs Count',
  `top_skills` json DEFAULT NULL COMMENT 'Top Skills',
  `etl_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL Time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_city` (`stat_date`,`city`),
  KEY `idx_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DWS Daily City Summary table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dws_monthly_industry_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dws_monthly_industry_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `stat_month` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Stat Month',
  `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Industry',
  `job_count` int DEFAULT '0' COMMENT 'Job Count',
  `avg_salary_min` decimal(10,2) DEFAULT NULL COMMENT 'Average Salary Minimum',
  `avg_salary_max` decimal(10,2) DEFAULT NULL COMMENT 'Average Salary Maximum',
  `growth_rate` decimal(6,2) DEFAULT NULL COMMENT 'Growth Rate',
  `top_skills` json DEFAULT NULL COMMENT 'Top Skills',
  `etl_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL Time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_month_industry` (`stat_month`,`industry`),
  KEY `idx_month` (`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DWS Monthly Industry Summary table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_label_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_label_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `label_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Label Name',
  `label_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Label Code',
  `label_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Label Type',
  `parent_id` bigint DEFAULT NULL COMMENT 'Parent ID',
  `alias_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Alias Name',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Description',
  `sort_no` int DEFAULT '0' COMMENT 'Sort No',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_label_name_type` (`label_name`,`label_type`),
  UNIQUE KEY `uk_label_code` (`label_code`),
  KEY `idx_label_parent` (`parent_id`),
  CONSTRAINT `fk_job_label_dict_parent` FOREIGN KEY (`parent_id`) REFERENCES `job_label_dict` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=70495 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Job Label Dictionary table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_label_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_label_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_posting_id` bigint NOT NULL COMMENT 'Job Posting ID',
  `label_id` bigint NOT NULL COMMENT 'Label ID',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Source Type',
  `confidence_score` decimal(5,2) DEFAULT NULL COMMENT 'Confidence Score',
  `is_core` tinyint NOT NULL DEFAULT '0' COMMENT 'Is Core',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_label_rel` (`job_posting_id`,`label_id`),
  KEY `idx_job_label_rel_label` (`label_id`),
  CONSTRAINT `fk_job_label_rel_job` FOREIGN KEY (`job_posting_id`) REFERENCES `biz_job_posting` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_job_label_rel_label` FOREIGN KEY (`label_id`) REFERENCES `job_label_dict` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=76704 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Job Label Relation table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_welfare_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_welfare_dict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `welfare_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Welfare Name',
  `welfare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Welfare Code',
  `welfare_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Welfare Type',
  `parent_id` bigint DEFAULT NULL COMMENT 'Parent ID',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Description',
  `sort_no` int DEFAULT '0' COMMENT 'Sort No',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_welfare_name` (`welfare_name`),
  UNIQUE KEY `uk_welfare_code` (`welfare_code`),
  KEY `idx_welfare_parent` (`parent_id`),
  CONSTRAINT `fk_job_welfare_dict_parent` FOREIGN KEY (`parent_id`) REFERENCES `job_welfare_dict` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Job Welfare Dictionary table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_welfare_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_welfare_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `job_posting_id` bigint NOT NULL COMMENT 'Job Posting ID',
  `welfare_id` bigint NOT NULL COMMENT 'Welfare ID',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Source Type',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_welfare_rel` (`job_posting_id`,`welfare_id`),
  KEY `idx_job_welfare_rel_welfare` (`welfare_id`),
  CONSTRAINT `fk_job_welfare_rel_job` FOREIGN KEY (`job_posting_id`) REFERENCES `biz_job_posting` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_job_welfare_rel_welfare` FOREIGN KEY (`welfare_id`) REFERENCES `job_welfare_dict` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=644874 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Job Welfare Relation table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `major_job_match_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major_job_match_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `major_id` bigint NOT NULL COMMENT 'Major ID',
  `job_category_id` bigint NOT NULL COMMENT 'Job Category ID',
  `match_weight` decimal(5,2) NOT NULL COMMENT 'Match Weight',
  `core_skills` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Core Skills',
  `rule_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Rule Source',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_job_rule` (`major_id`,`job_category_id`),
  KEY `fk_major_job_rule_category` (`job_category_id`),
  CONSTRAINT `fk_major_job_rule_category` FOREIGN KEY (`job_category_id`) REFERENCES `dim_job_category` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_major_job_rule_major` FOREIGN KEY (`major_id`) REFERENCES `dim_major` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Major Job Match Rule table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_api_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_api_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `api_key_id` bigint DEFAULT NULL COMMENT 'API Key ID',
  `endpoint` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Endpoint',
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Method',
  `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Request Parameters',
  `response_code` int DEFAULT NULL COMMENT 'Response Code',
  `response_time` int DEFAULT NULL COMMENT 'Response Time',
  `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'IP Address',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'User Agent',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_key` (`api_key_id`),
  KEY `idx_time` (`created_at`),
  KEY `idx_endpoint` (`endpoint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System API Call Log table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_api_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `api_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'API Key',
  `key_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Key Name',
  `permissions` json DEFAULT NULL COMMENT 'Permissions',
  `rate_limit_qps` int DEFAULT '10' COMMENT 'Rate Limit Qps',
  `daily_quota` int DEFAULT '1000' COMMENT 'Daily Quota',
  `is_active` tinyint DEFAULT '1' COMMENT 'Is Active',
  `expires_at` datetime DEFAULT NULL COMMENT 'Expires At',
  `last_used_at` datetime DEFAULT NULL COMMENT 'Last Used At',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `api_key` (`api_key`),
  KEY `idx_key` (`api_key`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `sys_api_key_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System API Key table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint DEFAULT NULL COMMENT 'User ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Username',
  `operation` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Operation',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Method',
  `request_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Request URL',
  `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'Request Parameters',
  `response_code` int DEFAULT NULL COMMENT 'Response Code',
  `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'IP Address',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'User Agent',
  `duration_ms` int DEFAULT NULL COMMENT 'Duration Ms',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_time` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System Operation Log table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Username',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Nickname',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Email',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Phone',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Password Hash',
  `avatar_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Avatar URL',
  `role_type` tinyint NOT NULL DEFAULT '0' COMMENT 'Role Type',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT 'Status',
  `last_login_at` datetime DEFAULT NULL COMMENT 'Last Login At',
  `last_login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Last Login IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`),
  KEY `idx_role_type` (`role_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System User table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` text NOT NULL,
  `channel` varchar(32) NOT NULL DEFAULT 'default',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0-disabled 1-enabled',
  `expire_time` datetime DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_config_key_channel` (`config_key`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System config table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `major_id` bigint DEFAULT NULL,
  `education_level` varchar(50) DEFAULT NULL,
  `target_region_code` varchar(20) DEFAULT NULL,
  `target_province_code` varchar(20) DEFAULT NULL,
  `target_city_code` varchar(20) DEFAULT NULL,
  `expected_salary_min` int DEFAULT NULL,
  `expected_salary_max` int DEFAULT NULL,
  `target_job_category_id` bigint DEFAULT NULL,
  `skills` text,
  `profile_summary` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_profile_user` (`user_id`),
  KEY `fk_user_profile_major` (`major_id`),
  KEY `fk_user_profile_region` (`target_region_code`),
  KEY `fk_user_profile_province` (`target_province_code`),
  KEY `fk_user_profile_city` (`target_city_code`),
  KEY `fk_user_profile_job_category` (`target_job_category_id`),
  CONSTRAINT `fk_user_profile_city` FOREIGN KEY (`target_city_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_profile_job_category` FOREIGN KEY (`target_job_category_id`) REFERENCES `dim_job_category` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_profile_major` FOREIGN KEY (`major_id`) REFERENCES `dim_major` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_profile_province` FOREIGN KEY (`target_province_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_profile_region` FOREIGN KEY (`target_region_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User profile table';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_report_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_report_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `preference_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Preference Name',
  `major_id` bigint DEFAULT NULL COMMENT 'Major ID',
  `region_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Region Code',
  `province_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Province Code',
  `city_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'City Code',
  `salary_min` int DEFAULT NULL COMMENT 'Salary Minimum',
  `salary_max` int DEFAULT NULL COMMENT 'Salary Maximum',
  `job_category_id` bigint DEFAULT NULL COMMENT 'Job Category ID',
  `company_name_keyword` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Name Keyword',
  `company_industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Industry',
  `company_size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Size',
  `company_finance` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Company Finance',
  `sort_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'Sort Type',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT 'Is Default',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created At',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated At',
  PRIMARY KEY (`id`),
  KEY `idx_pref_user` (`user_id`),
  KEY `idx_pref_major` (`major_id`),
  KEY `idx_pref_category` (`job_category_id`),
  KEY `fk_user_report_preference_region` (`region_code`),
  KEY `fk_user_report_preference_province` (`province_code`),
  KEY `fk_user_report_preference_city` (`city_code`),
  CONSTRAINT `fk_user_report_preference_city` FOREIGN KEY (`city_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_report_preference_job_category` FOREIGN KEY (`job_category_id`) REFERENCES `dim_job_category` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_report_preference_major` FOREIGN KEY (`major_id`) REFERENCES `dim_major` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_report_preference_province` FOREIGN KEY (`province_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_report_preference_region` FOREIGN KEY (`region_code`) REFERENCES `dim_region` (`region_code`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_report_preference_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User Report Preference table';
/*!40101 SET character_set_client = @saved_cs_client */;



