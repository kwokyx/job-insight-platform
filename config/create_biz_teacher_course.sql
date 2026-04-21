CREATE TABLE IF NOT EXISTS `biz_teacher_course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '教师用户ID',
  `course_name` VARCHAR(255) NOT NULL COMMENT '课程名称',
  `core_skills` TEXT COMMENT '核心技能点，逗号分隔',
  `credit_hours` INT DEFAULT NULL COMMENT '学时',
  `semester` VARCHAR(64) DEFAULT NULL COMMENT '开课学期',
  `major` VARCHAR(128) DEFAULT NULL COMMENT '所属专业',
  `description` TEXT COMMENT '课程描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_teacher_course_user` (`user_id`),
  KEY `idx_teacher_course_major` (`major`),
  KEY `idx_teacher_course_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师自建课程表';
