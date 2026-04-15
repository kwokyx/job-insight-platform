package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
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
}
