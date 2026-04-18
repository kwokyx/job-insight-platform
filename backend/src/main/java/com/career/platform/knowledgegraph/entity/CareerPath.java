package com.career.platform.knowledgegraph.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 职业路径（岗位间晋升/转型关系）
 */
@TableName("biz_career_path")
public class CareerPath {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobTitleFrom;
    private String jobTitleTo;
    private String transitionType;  // PROMOTION / LATERAL / PIVOT
    private BigDecimal avgYears;
    private String requiredSkills;  // JSON
    private Integer frequency;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobTitleFrom() { return jobTitleFrom; }
    public void setJobTitleFrom(String jobTitleFrom) { this.jobTitleFrom = jobTitleFrom; }
    public String getJobTitleTo() { return jobTitleTo; }
    public void setJobTitleTo(String jobTitleTo) { this.jobTitleTo = jobTitleTo; }
    public String getTransitionType() { return transitionType; }
    public void setTransitionType(String transitionType) { this.transitionType = transitionType; }
    public BigDecimal getAvgYears() { return avgYears; }
    public void setAvgYears(BigDecimal avgYears) { this.avgYears = avgYears; }
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
