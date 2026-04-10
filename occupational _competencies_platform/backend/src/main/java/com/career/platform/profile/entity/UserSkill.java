package com.career.platform.profile.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("biz_user_skill")
public class UserSkill {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long profileId;
    private Long skillId;
    private Integer proficiency;  // 1-5
    private String source;        // 自填/简历解析/课程成绩
}
