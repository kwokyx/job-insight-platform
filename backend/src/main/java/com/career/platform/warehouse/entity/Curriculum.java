package com.career.platform.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学校课程/教学大纲
 */
@Data
@TableName("biz_curriculum")
public class Curriculum {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseName;
    private String courseCode;
    private String department;
    private String major;
    private BigDecimal credit;
    private String semester;
    private String description;
    private String keywords;    // JSON array
    private Integer isActive;
    private Long uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
