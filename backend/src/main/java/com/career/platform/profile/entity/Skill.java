package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("job_label_dict")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("label_name")
    private String skillName;

    @TableField("label_type")
    private String category;

    @TableField(exist = false)
    private Integer hotScore;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getHotScore() { return hotScore; }
    public void setHotScore(Integer hotScore) { this.hotScore = hotScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
