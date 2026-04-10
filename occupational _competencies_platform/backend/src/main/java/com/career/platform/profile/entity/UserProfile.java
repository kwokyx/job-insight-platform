package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户画像实体
 */
@Data
@TableName("biz_user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String realName;
    private Integer gender;        // 0-女 1-男 2-未知
    private String university;
    private String major;
    private String education;
    private Integer graduationYear;
    private String preferredCities;     // JSON
    private String preferredIndustries; // JSON
    private String careerGoal;
    private String resumeUrl;
    private String resumeParsed;    // JSON
    private String skillVector;     // JSON
    private BigDecimal competencyScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
