package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCurriculumId() { return curriculumId; }
    public void setCurriculumId(Long curriculumId) { this.curriculumId = curriculumId; }

    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }

    public BigDecimal getRelevance() { return relevance; }
    public void setRelevance(BigDecimal relevance) { this.relevance = relevance; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
