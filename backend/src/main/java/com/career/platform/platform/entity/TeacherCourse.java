package com.career.platform.platform.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 教师课程数据实体（教师上传的课程大纲/培养方案）
 */
@TableName("biz_teacher_course")
public class TeacherCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;            // 教师用户ID
    private String courseName;      // 课程名称
    private String coreSkills;      // 核心技能点（逗号分隔）
    private Integer creditHours;    // 学时
    private String semester;        // 学期（如：2025-2026-1）
    private String major;           // 所属专业
    private String description;     // 课程描述

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getCoreSkills() { return coreSkills; }
    public void setCoreSkills(String coreSkills) { this.coreSkills = coreSkills; }

    public Integer getCreditHours() { return creditHours; }
    public void setCreditHours(Integer creditHours) { this.creditHours = creditHours; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
