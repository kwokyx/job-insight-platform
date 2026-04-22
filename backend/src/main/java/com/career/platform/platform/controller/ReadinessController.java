package com.career.platform.platform.controller;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.platform.service.ReadinessService;
import com.career.platform.system.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@Tag(name = "Readiness", description = "Role-specific prerequisite readiness")
@RestController
@RequestMapping("/api/v1/readiness")
public class ReadinessController {

    private final ReadinessService readinessService;

    public ReadinessController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @Operation(summary = "Get current user readiness")
    @GetMapping("/{roleType}")
    public R<?> getReadiness(@PathVariable String roleType) {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer expectedRoleType = parseRoleType(roleType);
        Integer currentRoleType = SecurityUtils.getCurrentRoleType();
        if (!expectedRoleType.equals(currentRoleType) && currentRoleType != SysUser.ROLE_ADMIN) {
            throw BusinessException.of(403, "权限不足，当前账号无法访问该能力", "READINESS_ROLE_FORBIDDEN");
        }
        Map<String, Object> payload = readinessService.buildReadiness(userId, expectedRoleType);
        return R.ok(payload);
    }

    private Integer parseRoleType(String raw) {
        if (raw == null) {
            return SysUser.ROLE_USER;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "ADMIN":
            case "1":
                return SysUser.ROLE_ADMIN;
            case "TEACHER":
            case "2":
                return SysUser.ROLE_TEACHER;
            case "STUDENT":
            case "USER":
            case "0":
            default:
                return SysUser.ROLE_USER;
        }
    }
}
