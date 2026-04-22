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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Tag(name = "管理后台", description = "用户管理、仪表盘、操作日志")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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

    @Log("查看管理仪表盘")
    @Operation(summary = "管理仪表盘概览")
    @GetMapping("/dashboard")
    public R<?> dashboard() {
        Map<String, Object> data = new HashMap<>();

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

        long totalJobs = jobMapper.selectCount(null);
        long newJobs7d = jobMapper.countJobsSince(LocalDate.now().minusDays(7));
        data.put("totalJobs", totalJobs);
        data.put("newJobs7d", newJobs7d);

        long totalReports = reportMapper.selectCount(null);
        data.put("totalReports", totalReports);

        List<Map<String, Object>> registrationTrend = jobMapper.userRegistrationTrend(LocalDate.now().minusDays(30));
        data.put("registrationTrend", registrationTrend);

        List<OperationLog> recentLogs = logMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .orderByDesc(OperationLog::getCreatedAt)
                        .last("LIMIT 10")
        );
        data.put("recentLogs", recentLogs);

        return R.ok(data);
    }

    @Log("查看用户列表")
    @Operation(summary = "用户列表（分页、搜索）")
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

        IPage<SysUser> result = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        result.getRecords().forEach(u -> u.setPasswordHash(null));

        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    public static class UpdateUserProfileRequest {
        private String nickname;
        private String email;
        private String phone;
        private String avatarUrl;
        private Integer roleType;
        private Integer status;

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public Integer getRoleType() {
            return roleType;
        }

        public void setRoleType(Integer roleType) {
            this.roleType = roleType;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    @Log("编辑用户资料")
    @Operation(summary = "管理员编辑用户资料")
    @PutMapping("/users/{id}")
    public R<?> updateUserProfile(@PathVariable Long id, @Valid @RequestBody UpdateUserProfileRequest req) {
        Long currentUserId = getCurrentUserId();
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        if (req.getRoleType() != null) {
            validateRoleType(req.getRoleType());
        }
        if (req.getStatus() != null) {
            validateStatus(req.getStatus());
        }
        if (currentUserId.equals(id)) {
            if (req.getRoleType() != null && !req.getRoleType().equals(user.getRoleType())) {
                throw BusinessException.of(400, "不能通过资料编辑修改自己的角色");
            }
            if (req.getStatus() != null && !req.getStatus().equals(user.getStatus())) {
                throw BusinessException.of(400, "不能通过资料编辑修改自己的状态");
            }
        }

        String nickname = normalize(req.getNickname());
        String email = normalize(req.getEmail());
        String phone = normalize(req.getPhone());
        String avatarUrl = normalize(req.getAvatarUrl());

        if (StringUtils.hasText(email)) {
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw BusinessException.of(400, "邮箱格式不正确");
            }
            SysUser existing = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getEmail, email)
                            .ne(SysUser::getId, id)
                            .last("LIMIT 1")
            );
            if (existing != null) {
                throw BusinessException.of(400, "该邮箱已被其他用户使用");
            }
        }

        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setAvatarUrl(avatarUrl);
        if (req.getRoleType() != null) {
            user.setRoleType(req.getRoleType());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        user.setPasswordHash(null);

        return R.ok(user);
    }

    public static class UpdateStatusRequest {
        @NotNull(message = "状态不能为空")
        private Integer status;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    @Log("修改用户状态")
    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    public R<?> updateUserStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(id)) {
            throw BusinessException.of(400, "不能修改自己的状态");
        }
        validateStatus(req.getStatus());

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setStatus(req.getStatus());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return R.ok("用户状态已更新");
    }

    public static class UpdateRoleRequest {
        @NotNull(message = "角色不能为空")
        private Integer roleType;

        public Integer getRoleType() {
            return roleType;
        }

        public void setRoleType(Integer roleType) {
            this.roleType = roleType;
        }
    }

    @Log("修改用户角色")
    @Operation(summary = "切换用户角色")
    @PutMapping("/users/{id}/role")
    public R<?> updateUserRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest req) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId.equals(id)) {
            throw BusinessException.of(400, "不能修改自己的角色");
        }
        validateRoleType(req.getRoleType());

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setRoleType(req.getRoleType());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return R.ok("用户角色已更新");
    }

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

    private void validateRoleType(Integer roleType) {
        if (roleType == null || roleType < 0 || roleType > 2) {
            throw BusinessException.of(400, "角色类型无效，仅支持 0-学生 1-管理员 2-教师");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw BusinessException.of(400, "状态值无效，仅支持 0-禁用 1-正常 2-锁定");
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
