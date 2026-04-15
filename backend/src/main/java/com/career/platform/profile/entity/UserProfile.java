package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
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
}
