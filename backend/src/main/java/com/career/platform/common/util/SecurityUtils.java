package com.career.platform.common.util;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.system.entity.SysUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 统一提取当前登录用户信息的安全工具类。
 */
public final class SecurityUtils {

    public static final int ROLE_STUDENT = SysUser.ROLE_USER;
    public static final int ROLE_ADMIN = SysUser.ROLE_ADMIN;
    public static final int ROLE_TEACHER = SysUser.ROLE_TEACHER;

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID
     * @throws BusinessException 未登录或认证主体异常时抛出 401
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("登录状态已失效，请重新登录");
        }
        if (auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        throw BusinessException.unauthorized("登录状态异常，请重新登录");
    }

    /**
     * 获取当前登录用户 ID，未登录时返回 null。
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

    public static Integer getCurrentRoleType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getCredentials() instanceof Integer)) {
            return SysUser.ROLE_USER;
        }
        return (Integer) auth.getCredentials();
    }

    public static boolean isAdmin() {
        return ROLE_ADMIN == getCurrentRoleType();
    }

    public static boolean isTeacher() {
        return ROLE_TEACHER == getCurrentRoleType();
    }

    public static boolean isStudent() {
        return ROLE_STUDENT == getCurrentRoleType();
    }

    public static Long resolveOwnedUserId(Long requestedUserId) {
        Long currentUserId = getCurrentUserId();
        if (requestedUserId == null) {
            return currentUserId;
        }
        if (isAdmin()) {
            return requestedUserId;
        }
        if (!requestedUserId.equals(currentUserId)) {
            throw BusinessException.forbidden("当前账号无权查看其他用户的数据");
        }
        return currentUserId;
    }

    public static void requireSelfOrAdmin(Long ownerUserId) {
        if (ownerUserId == null) {
            throw BusinessException.notFound("目标数据不存在");
        }
        if (isAdmin()) {
            return;
        }
        if (!ownerUserId.equals(getCurrentUserId())) {
            throw BusinessException.forbidden("当前账号无权访问该数据");
        }
    }
}
