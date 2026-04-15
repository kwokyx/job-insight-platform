package com.career.platform.knowledgegraph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 技能关系（共现、上下位、相似、前置）
 */
@Data
@TableName("biz_skill_relation")
public class SkillRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skillIdA;
    private Long skillIdB;
    private String relationType;    // CO_OCCUR / PARENT_CHILD / SIMILAR / PREREQUISITE
    private BigDecimal weight;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
