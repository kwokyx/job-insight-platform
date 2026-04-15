package com.career.platform.knowledgegraph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 职业路径（岗位间晋升/转型关系）
 */
@Data
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
}
