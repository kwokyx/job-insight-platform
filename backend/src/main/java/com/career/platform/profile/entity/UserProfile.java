package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_profile")
public class UserProfile {

    @TableId
    private Long id;

    private Long userId;
    private Long majorId;
    private String educationLevel;
    private String targetRegionCode;
    private String targetProvinceCode;
    private String targetCityCode;
    private Integer expectedSalaryMin;
    private Integer expectedSalaryMax;
    private Long targetJobCategoryId;
    private String skills;
    private String profileSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }
    public String getTargetRegionCode() { return targetRegionCode; }
    public void setTargetRegionCode(String targetRegionCode) { this.targetRegionCode = targetRegionCode; }
    public String getTargetProvinceCode() { return targetProvinceCode; }
    public void setTargetProvinceCode(String targetProvinceCode) { this.targetProvinceCode = targetProvinceCode; }
    public String getTargetCityCode() { return targetCityCode; }
    public void setTargetCityCode(String targetCityCode) { this.targetCityCode = targetCityCode; }
    public Integer getExpectedSalaryMin() { return expectedSalaryMin; }
    public void setExpectedSalaryMin(Integer expectedSalaryMin) { this.expectedSalaryMin = expectedSalaryMin; }
    public Integer getExpectedSalaryMax() { return expectedSalaryMax; }
    public void setExpectedSalaryMax(Integer expectedSalaryMax) { this.expectedSalaryMax = expectedSalaryMax; }
    public Long getTargetJobCategoryId() { return targetJobCategoryId; }
    public void setTargetJobCategoryId(Long targetJobCategoryId) { this.targetJobCategoryId = targetJobCategoryId; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getProfileSummary() { return profileSummary; }
    public void setProfileSummary(String profileSummary) { this.profileSummary = profileSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
