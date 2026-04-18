package com.career.platform.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.auth.util.JwtUtil;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(SysUserMapper userMapper, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        private String email;
        private String nickname;
        private Integer roleType;  // 0-学生 2-教师（不允许自注册管理员）

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Integer getRoleType() { return roleType; }
        public void setRoleType(Integer roleType) { this.roleType = roleType; }
    }

    @Log("用户注册")
    @PostMapping("/register")
    public R<?> register(@Valid @RequestBody RegisterRequest req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        if (count > 0) {
            throw BusinessException.of(400, "用户名已存在");
        }

        if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
            Long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, req.getEmail())
            );
            if (emailCount > 0) {
                throw BusinessException.of(400, "邮箱已被注册");
            }
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        // 角色：0=学生(默认)，2=教师，不允许自注册管理员(1)
        int role = (req.getRoleType() != null && req.getRoleType() == 2) ? 2 : 0;
        user.setRoleType(role);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        return R.ok("注册成功", data);
    }

    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @Log("用户登录")
    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        if (user == null) {
            throw BusinessException.of(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw BusinessException.of(403, "账号已被禁用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BusinessException.of(401, "用户名或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRoleType());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", jwtUtil.getAccessTokenExpire());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
        userInfo.put("roleType", user.getRoleType());
        userInfo.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        result.put("user", userInfo);

        return R.ok("登录成功", result);
    }

    // ─── Token 刷新端点 ─────────────────

    public static class RefreshRequest {
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    @PostMapping("/refresh")
    public R<?> refreshToken(@Valid @RequestBody RefreshRequest req) {
        // 验证 refreshToken
        if (!jwtUtil.validateToken(req.getRefreshToken())) {
            throw BusinessException.of(401, "Refresh Token 无效或已过期");
        }

        Claims claims = jwtUtil.parseToken(req.getRefreshToken());
        String tokenType = claims.get("tokenType", String.class);
        if (!"refresh".equals(tokenType)) {
            throw BusinessException.of(401, "Token 类型错误");
        }

        Long userId = Long.parseLong(claims.getSubject());
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw BusinessException.of(401, "用户不存在或已被禁用");
        }

        // 签发新 accessToken
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRoleType());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("expiresIn", jwtUtil.getAccessTokenExpire());

        return R.ok("Token 刷新成功", result);
    }

    // ─── 修改密码 ────────────────────────

    public static class ChangePasswordRequest {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @Log("修改密码")
    @PutMapping("/password")
    public R<?> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw BusinessException.of(400, "旧密码不正确");
        }

        if (req.getNewPassword().length() < 6) {
            throw BusinessException.of(400, "新密码长度至少6位");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return R.ok("密码修改成功");
    }

    @GetMapping("/profile")
    public R<?> getProfile() {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("nickname", user.getNickname());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("roleType", user.getRoleType());
        profile.put("lastLoginAt", user.getLastLoginAt());
        profile.put("createdAt", user.getCreatedAt());

        return R.ok(profile);
    }

    public static class UpdateProfileRequest {
        private String nickname;
        private String email;
        private String phone;
        private String avatarUrl;

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    @Log("更新个人信息")
    @PutMapping("/profile")
    public R<?> updateProfile(@RequestBody UpdateProfileRequest req) {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl());
        }
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.updateById(user);
        return R.ok("更新成功");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return (Long) auth.getPrincipal();
    }
}
