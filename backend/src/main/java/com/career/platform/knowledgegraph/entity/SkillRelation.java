package com.career.platform.knowledgegraph.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 技能关系（共现、上下位、相似、前置）
 */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSkillIdA() { return skillIdA; }
    public void setSkillIdA(Long skillIdA) { this.skillIdA = skillIdA; }
    public Long getSkillIdB() { return skillIdB; }
    public void setSkillIdB(Long skillIdB) { this.skillIdB = skillIdB; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
