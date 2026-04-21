package com.career.platform.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.auth.service.AuthThrottleService;
import com.career.platform.auth.service.CaptchaService;
import com.career.platform.auth.service.LoginAttemptService;
import com.career.platform.auth.service.PasswordResetService;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
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
    private final LoginAttemptService loginAttemptService;
    private final CaptchaService captchaService;
    private final AuthThrottleService authThrottleService;
    private final PasswordResetService passwordResetService;

    public AuthController(SysUserMapper userMapper,
                          JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptService loginAttemptService,
                          CaptchaService captchaService,
                          AuthThrottleService authThrottleService,
                          PasswordResetService passwordResetService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.captchaService = captchaService;
        this.authThrottleService = authThrottleService;
        this.passwordResetService = passwordResetService;
    }

    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String email;
        private String nickname;
        private Integer roleType;
        private String captchaId;
        private String captchaCode;

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
        public String getCaptchaId() { return captchaId; }
        public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
        public String getCaptchaCode() { return captchaCode; }
        public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
    }

    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String captchaId;
        private String captchaCode;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getCaptchaId() { return captchaId; }
        public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
        public String getCaptchaCode() { return captchaCode; }
        public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
    }

    public static class RefreshRequest {
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

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

    public static class PasswordResetRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "邮箱不能为空")
        private String email;
        @NotBlank(message = "验证码ID不能为空")
        private String captchaId;
        @NotBlank(message = "验证码不能为空")
        private String captchaCode;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCaptchaId() { return captchaId; }
        public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
        public String getCaptchaCode() { return captchaCode; }
        public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
    }

    public static class PasswordResetConfirmRequest {
        @NotBlank(message = "重置令牌不能为空")
        private String resetToken;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;

        public String getResetToken() { return resetToken; }
        public void setResetToken(String resetToken) { this.resetToken = resetToken; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @Log("用户注册")
    @PostMapping("/register")
    public R<?> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request) {
        authThrottleService.checkRegisterAllowed(request);
        try {
            captchaService.verify(req.getCaptchaId(), req.getCaptchaCode());
            validatePasswordStrength(req.getPassword());

            long userCount = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername().trim())
            );
            if (userCount > 0) {
                throw BusinessException.of(400, "用户名已存在");
            }

            if (StringUtils.hasText(req.getEmail())) {
                long emailCount = userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, req.getEmail().trim())
                );
                if (emailCount > 0) {
                    throw BusinessException.of(400, "邮箱已被注册");
                }
            }

            SysUser user = new SysUser();
            user.setUsername(req.getUsername().trim());
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            user.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim() : null);
            user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname().trim() : req.getUsername().trim());
            user.setRoleType(resolveRegisterRole(req.getRoleType()));
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);

            authThrottleService.onRegisterSuccess(request);
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("roleType", user.getRoleType());
            return R.ok("注册成功", data);
        } catch (BusinessException ex) {
            authThrottleService.onRegisterFailure(request);
            throw ex;
        }
    }

    @GetMapping("/captcha")
    public R<?> captcha(@RequestParam(required = false) String type) {
        return R.ok(captchaService.createCaptcha(type));
    }

    @Log("用户登录")
    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String username = req.getUsername().trim();
        authThrottleService.checkLoginAllowed(request);
        loginAttemptService.checkAllowed(username);
        try {
            captchaService.verify(req.getCaptchaId(), req.getCaptchaCode());

            SysUser user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
            );
            if (user == null) {
                loginAttemptService.onFailure(username);
                throw BusinessException.of(401, "用户名或密码错误");
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw BusinessException.of(403, "账号已被禁用");
            }
            if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                loginAttemptService.onFailure(username);
                throw BusinessException.of(401, "用户名或密码错误");
            }

            loginAttemptService.onSuccess(username);
            authThrottleService.onLoginSuccess(request);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);

            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRoleType()));
            result.put("refreshToken", jwtUtil.generateRefreshToken(user.getId()));
            result.put("expiresIn", jwtUtil.getAccessTokenExpire());
            result.put("user", buildUserPayload(user));
            return R.ok("登录成功", result);
        } catch (BusinessException ex) {
            if (ex.getCode() != 429) {
                authThrottleService.onLoginFailure(request);
            }
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public R<?> refreshToken(@Valid @RequestBody RefreshRequest req) {
        if (!jwtUtil.validateToken(req.getRefreshToken())) {
            throw BusinessException.of(401, "Refresh Token 无效或已过期");
        }

        Claims claims = jwtUtil.parseToken(req.getRefreshToken());
        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw BusinessException.of(401, "Token 类型错误");
        }

        Long userId = Long.parseLong(claims.getSubject());
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.of(401, "用户不存在或已被禁用");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRoleType()));
        result.put("expiresIn", jwtUtil.getAccessTokenExpire());
        return R.ok("Token 刷新成功", result);
    }

    @Log("找回密码申请")
    @PostMapping("/password/reset/request")
    public R<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        captchaService.verify(req.getCaptchaId(), req.getCaptchaCode());
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (user == null || !StringUtils.hasText(user.getEmail())
                || !user.getEmail().trim().equalsIgnoreCase(req.getEmail().trim())) {
            throw BusinessException.of(400, "用户名和邮箱不匹配");
        }

        Map<String, Object> result = new HashMap<>(passwordResetService.issueResetToken(user.getId(), user.getUsername()));
        result.put("maskedEmail", maskEmail(user.getEmail()));
        return R.ok("找回密码校验通过", result);
    }

    @Log("重置密码")
    @PostMapping("/password/reset/confirm")
    public R<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        validatePasswordStrength(req.getNewPassword());
        Long userId = passwordResetService.consumeResetToken(req.getResetToken());
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return R.ok("密码重置成功", null);
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

        validatePasswordStrength(req.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return R.ok("密码修改成功", null);
    }

    @GetMapping("/profile")
    public R<?> getProfile() {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        Map<String, Object> profile = buildUserPayload(user);
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("lastLoginAt", user.getLastLoginAt());
        profile.put("createdAt", user.getCreatedAt());
        return R.ok(profile);
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
        return R.ok("更新成功", null);
    }

    private Map<String, Object> buildUserPayload(SysUser user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        userInfo.put("roleType", user.getRoleType());
        userInfo.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        return userInfo;
    }

    private Integer resolveRegisterRole(Integer roleType) {
        if (roleType != null && roleType == SysUser.ROLE_TEACHER) {
            return SysUser.ROLE_TEACHER;
        }
        return SysUser.ROLE_USER;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return (Long) authentication.getPrincipal();
    }

    private void validatePasswordStrength(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw BusinessException.of(400, "密码长度至少 8 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw BusinessException.of(400, "密码必须同时包含字母和数字");
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return maskedLocal + "@" + parts[1];
    }
}
