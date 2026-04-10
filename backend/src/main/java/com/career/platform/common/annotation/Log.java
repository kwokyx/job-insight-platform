package com.career.platform.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解 — 加在 Controller 方法上自动记录操作日志
 * 用法: @Log("用户登录")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    String value() default "";
}
