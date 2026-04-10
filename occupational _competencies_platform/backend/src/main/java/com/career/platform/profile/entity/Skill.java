package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String skillName;
    private String category;
    private Integer hotScore;
    private LocalDateTime createdAt;
}
