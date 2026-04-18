package com.career.platform.common.util;

import com.career.platform.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类 — 统一提取当前登录用户 ID
 * 替代各 Controller 中散落的 SecurityContextHolder 重复代码（~5 处）
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 获取当前登录用户 ID（强制鉴权版）
     *
     * @return userId 非 null
     * @throws BusinessException 401 if not authenticated
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("Please login first");
        }
        if (auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        throw BusinessException.unauthorized("Invalid login state");
    }

    /**
     * 获取当前登录用户 ID（可选版，未登录返回 null）
     *
     * @return userId or null
     */
    public static Long getCurrentUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long) {
                return (Long) auth.getPrincipal();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
