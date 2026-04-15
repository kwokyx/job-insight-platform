package com.career.platform.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String passwordHash;
    private String avatarUrl;

    /** 0-普通用户/学生 1-管理员 2-教师 */
    private Integer roleType;

    /** 角色常量 */
    @TableField(exist = false)
    public static final int ROLE_USER = 0;
    @TableField(exist = false)
    public static final int ROLE_ADMIN = 1;
    @TableField(exist = false)
    public static final int ROLE_TEACHER = 2;

    /** 0-禁用 1-正常 2-锁定 */
    private Integer status;

    private LocalDateTime lastLoginAt;
    private String lastLoginIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
