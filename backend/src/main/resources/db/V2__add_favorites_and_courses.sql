-- 岗位收藏表
CREATE TABLE IF NOT EXISTS `biz_job_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `job_id` BIGINT NOT NULL,
  `note` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_job` (`user_id`, `job_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位收藏';

-- 教师课程表
CREATE TABLE IF NOT EXISTS `biz_teacher_course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `course_name` VARCHAR(200) NOT NULL,
  `core_skills` TEXT NOT NULL COMMENT '核心技能点,逗号分隔',
  `credit_hours` INT DEFAULT NULL COMMENT '学时',
  `semester` VARCHAR(50) DEFAULT NULL COMMENT '学期',
  `major` VARCHAR(100) DEFAULT NULL COMMENT '所属专业',
  `description` TEXT DEFAULT NULL COMMENT '课程描述',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师课程数据';
