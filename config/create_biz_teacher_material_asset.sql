CREATE TABLE IF NOT EXISTS `biz_teacher_material_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '教师用户ID',
  `material_type` VARCHAR(64) NOT NULL COMMENT '资料类型',
  `material_name` VARCHAR(128) NOT NULL COMMENT '资料名称',
  `major` VARCHAR(128) DEFAULT NULL COMMENT '关联专业',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  `row_count` INT DEFAULT 0 COMMENT '导入行数',
  `summary_json` LONGTEXT COMMENT '导入摘要',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_teacher_material_user_type` (`user_id`, `material_type`),
  KEY `idx_teacher_material_major` (`major`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师教学资料上传记录';
