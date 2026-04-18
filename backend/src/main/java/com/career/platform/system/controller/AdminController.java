package com.career.platform.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.system.entity.OperationLog;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.OperationLogMapper;
import com.career.platform.system.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台控制器（仅管理员）
 */
@Tag(name = "管理后台", description = "用户管理、仪表盘、操作日志")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final SysUserMapper userMapper;
    private final OperationLogMapper logMapper;
    private final JobPostingMapper jobMapper;
    private final AnalysisReportMapper reportMapper;

    public AdminController(SysUserMapper userMapper, OperationLogMapper logMapper,
                           JobPostingMapper jobMapper, AnalysisReportMapper reportMapper) {
        this.userMapper = userMapper;
        this.logMapper = logMapper;
        this.jobMapper = jobMapper;
        this.reportMapper = reportMapper;
    }

    // ─── 管理仪表盘 ─────────────────────

    @Log("查看管理仪表盘")
    @Operation(summary = "管理仪表盘（概览数据）")
    @GetMapping("/dashboard")
    public R<?> dashboard() {
        Map<String, Object> data = new HashMap<>();

        // 用户统计
        long totalUsers = userMapper.selectCount(null);
        long newUsersToday = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .ge(SysUser::getCreatedAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN))
        );
        long studentCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, 0));
        long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, 1));
        long teacherCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleType, 2));
        long bannedCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 0));
        long activeToday = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .ge(SysUser::getLastLoginAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN)));

        data.put("totalUsers", totalUsers);
        data.put("newUsersToday", newUsersToday);
        data.put("studentCount", studentCount);
        data.put("adminCount", adminCount);
        data.put("teacherCount", teacherCount);
        data.put("bannedCount", bannedCount);
        data.put("activeToday", activeToday);

        // 职位统计
        long totalJobs = jobMapper.selectCount(null);
        long newJobs7d = jobMapper.countJobsSince(LocalDate.now().minusDays(7));
        data.put("totalJobs", totalJobs);
        data.put("newJobs7d", newJobs7d);

        // 报告统计
        long totalReports = reportMapper.selectCount(null);
        data.put("totalReports", totalReports);

        // 30天注册趋势
        List<Map<String, Object>> registrationTrend = jobMapper.userRegistrationTrend(LocalDate.now().minusDays(30));
        data.put("registrationTrend", registrationTrend);

        // 最近操作日志
        List<OperationLog> recentLogs = logMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .orderByDesc(OperationLog::getCreatedAt)
                        .last("LIMIT 10")
        );
        data.put("recentLogs", recentLogs);

        return R.ok(data);
    }

    // ─── 用户列表 ────────────────────────

    @Log("查看用户列表")
    @Operation(summary = "用户列表（分页+搜索）")
    @GetMapping("/users")
    public R<?> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer roleType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getEmail, keyword)
            );
        }
        if (roleType != null) {
            wrapper.eq(SysUser::getRoleType, roleType);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }

        wrapper.orderByDesc(SysUser::getCreatedAt);

        // 敏感字段 passwordHash 不返回给前端
        IPage<SysUser> result = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        result.getRecords().forEach(u -> u.setPasswordHash(null));

        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    // ─── 启用/禁用用户 ──────────────────

    public static class UpdateStatusRequest {
        @NotNull(message = "状态不能为空")
        private Integer status;  // 0-禁用 1-正常

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    @Log("修改用户状态")
    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    public R<?> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(id)) {
            throw BusinessException.of(400, "不能修改自己的状态");
        }

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setStatus(req.getStatus());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return R.ok("用户状态已更新");
    }

    // ─── 切换角色 ────────────────────────

    public static class UpdateRoleRequest {
        @NotNull(message = "角色不能为空")
        private Integer roleType;  // 0-普通用户/学生 1-管理员 2-教师

        public Integer getRoleType() { return roleType; }
        public void setRoleType(Integer roleType) { this.roleType = roleType; }
    }

    @Log("修改用户角色")
    @Operation(summary = "切换用户角色")
    @PutMapping("/users/{id}/role")
    public R<?> updateUserRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest req) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(id)) {
            throw BusinessException.of(400, "不能修改自己的角色");
        }
        if (req.getRoleType() < 0 || req.getRoleType() > 2) {
            throw BusinessException.of(400, "角色类型无效（0-学生 1-管理员 2-教师）");
        }

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setRoleType(req.getRoleType());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return R.ok("用户角色已更新");
    }

    // ─── 操作日志查询 ────────────────────

    @Operation(summary = "操作日志查询（分页）")
    @GetMapping("/logs")
    public R<?> queryLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLog::getUsername, username);
        }
        if (StringUtils.hasText(operation)) {
            wrapper.like(OperationLog::getOperation, operation);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(OperationLog::getCreatedAt,
                    LocalDateTime.of(LocalDate.parse(startDate), LocalTime.MIN));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(OperationLog::getCreatedAt,
                    LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX));
        }

        wrapper.orderByDesc(OperationLog::getCreatedAt);

        IPage<OperationLog> result = logMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    // ─── 工具方法 ────────────────────────

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
