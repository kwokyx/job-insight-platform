package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_curriculum_skill_mapping")
public class CurriculumSkillMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long curriculumId;
    private Long skillId;
    private BigDecimal relevance;
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
