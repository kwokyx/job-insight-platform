package com.career.platform.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.auth.service.AuthMailService;
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

    private static final String MSG_USERNAME_REQUIRED = "\u7528\u6237\u540d\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_PASSWORD_REQUIRED = "\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_EMAIL_REQUIRED = "\u90ae\u7bb1\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_CAPTCHA_ID_REQUIRED = "\u9a8c\u8bc1\u7801ID\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_CAPTCHA_REQUIRED = "\u9a8c\u8bc1\u7801\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_EMAIL_CODE_REQUIRED = "\u90ae\u7bb1\u9a8c\u8bc1\u7801\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_REFRESH_TOKEN_REQUIRED = "refreshToken \u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_OLD_PASSWORD_REQUIRED = "\u65e7\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MSG_NEW_PASSWORD_REQUIRED = "\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a";

    private static final String MSG_USERNAME_EXISTS = "\u7528\u6237\u540d\u5df2\u5b58\u5728";
    private static final String MSG_EMAIL_REGISTERED = "\u90ae\u7bb1\u5df2\u88ab\u6ce8\u518c";
    private static final String MSG_REGISTER_SUCCESS = "\u6ce8\u518c\u6210\u529f";
    private static final String MSG_LOGIN_FAILED = "\u7528\u6237\u540d\u6216\u5bc6\u7801\u9519\u8bef";
    private static final String MSG_ACCOUNT_DISABLED = "\u8d26\u53f7\u5df2\u88ab\u7981\u7528";
    private static final String MSG_LOGIN_SUCCESS = "\u767b\u5f55\u6210\u529f";
    private static final String MSG_REFRESH_TOKEN_INVALID = "Refresh Token \u65e0\u6548\u6216\u5df2\u8fc7\u671f";
    private static final String MSG_TOKEN_TYPE_INVALID = "Token \u7c7b\u578b\u9519\u8bef";
    private static final String MSG_USER_MISSING_OR_DISABLED = "\u7528\u6237\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u7981\u7528";
    private static final String MSG_REFRESH_SUCCESS = "Token \u5237\u65b0\u6210\u529f";
    private static final String MSG_RESET_IDENTITY_MISMATCH = "\u7528\u6237\u540d\u548c\u90ae\u7bb1\u4e0d\u5339\u914d";
    private static final String MSG_MAIL_DISABLED = "\u90ae\u4ef6\u670d\u52a1\u672a\u5f00\u542f\uff0c\u6682\u65f6\u65e0\u6cd5\u627e\u56de\u5bc6\u7801";
    private static final String MSG_MAIL_CODE_SENT = "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001\u81f3\u7ed1\u5b9a\u90ae\u7bb1";
    private static final String MSG_USER_NOT_FOUND = "\u7528\u6237\u4e0d\u5b58\u5728";
    private static final String MSG_RESET_VERIFY_MISMATCH = "\u9a8c\u8bc1\u7801\u6821\u9a8c\u4fe1\u606f\u4e0e\u8d26\u53f7\u4e0d\u5339\u914d";
    private static final String MSG_RESET_SUCCESS = "\u5bc6\u7801\u91cd\u7f6e\u6210\u529f";
    private static final String MSG_OLD_PASSWORD_INVALID = "\u65e7\u5bc6\u7801\u4e0d\u6b63\u786e";
    private static final String MSG_CHANGE_PASSWORD_SUCCESS = "\u5bc6\u7801\u4fee\u6539\u6210\u529f";
    private static final String MSG_UPDATE_SUCCESS = "\u66f4\u65b0\u6210\u529f";
    private static final String MSG_LOGIN_REQUIRED = "\u8bf7\u5148\u767b\u5f55";
    private static final String MSG_PASSWORD_TOO_SHORT = "\u5bc6\u7801\u957f\u5ea6\u81f3\u5c11 8 \u4f4d";
    private static final String MSG_PASSWORD_RULE_INVALID = "\u5bc6\u7801\u5fc5\u987b\u540c\u65f6\u5305\u542b\u5b57\u6bcd\u548c\u6570\u5b57";

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaService captchaService;
    private final AuthThrottleService authThrottleService;
    private final PasswordResetService passwordResetService;
    private final AuthMailService authMailService;

    public AuthController(SysUserMapper userMapper,
                          JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          LoginAttemptService loginAttemptService,
                          CaptchaService captchaService,
                          AuthThrottleService authThrottleService,
                          PasswordResetService passwordResetService,
                          AuthMailService authMailService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.captchaService = captchaService;
        this.authThrottleService = authThrottleService;
        this.passwordResetService = passwordResetService;
        this.authMailService = authMailService;
    }

    public static class RegisterRequest {
        @NotBlank(message = MSG_USERNAME_REQUIRED)
        private String username;
        @NotBlank(message = MSG_PASSWORD_REQUIRED)
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
        @NotBlank(message = MSG_USERNAME_REQUIRED)
        private String username;
        @NotBlank(message = MSG_PASSWORD_REQUIRED)
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
        @NotBlank(message = MSG_REFRESH_TOKEN_REQUIRED)
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class ChangePasswordRequest {
        @NotBlank(message = MSG_OLD_PASSWORD_REQUIRED)
        private String oldPassword;
        @NotBlank(message = MSG_NEW_PASSWORD_REQUIRED)
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
        @NotBlank(message = MSG_USERNAME_REQUIRED)
        private String username;
        @NotBlank(message = MSG_EMAIL_REQUIRED)
        private String email;
        @NotBlank(message = MSG_CAPTCHA_ID_REQUIRED)
        private String captchaId;
        @NotBlank(message = MSG_CAPTCHA_REQUIRED)
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
        @NotBlank(message = MSG_USERNAME_REQUIRED)
        private String username;
        @NotBlank(message = MSG_EMAIL_REQUIRED)
        private String email;
        @NotBlank(message = MSG_EMAIL_CODE_REQUIRED)
        private String emailCode;
        @NotBlank(message = MSG_NEW_PASSWORD_REQUIRED)
        private String newPassword;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getEmailCode() { return emailCode; }
        public void setEmailCode(String emailCode) { this.emailCode = emailCode; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @Log("\u7528\u6237\u6ce8\u518c")
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
                throw BusinessException.of(400, MSG_USERNAME_EXISTS);
            }

            if (StringUtils.hasText(req.getEmail())) {
                long emailCount = userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, req.getEmail().trim())
                );
                if (emailCount > 0) {
                    throw BusinessException.of(400, MSG_EMAIL_REGISTERED);
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
            return R.ok(MSG_REGISTER_SUCCESS, data);
        } catch (BusinessException ex) {
            authThrottleService.onRegisterFailure(request);
            throw ex;
        }
    }

    @GetMapping("/captcha")
    public R<?> captcha(@RequestParam(required = false) String type) {
        return R.ok(captchaService.createCaptcha(type));
    }

    @Log("\u7528\u6237\u767b\u5f55")
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
                throw BusinessException.of(401, MSG_LOGIN_FAILED);
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw BusinessException.of(403, MSG_ACCOUNT_DISABLED);
            }
            if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                loginAttemptService.onFailure(username);
                throw BusinessException.of(401, MSG_LOGIN_FAILED);
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
            return R.ok(MSG_LOGIN_SUCCESS, result);
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
            throw BusinessException.of(401, MSG_REFRESH_TOKEN_INVALID);
        }

        Claims claims = jwtUtil.parseToken(req.getRefreshToken());
        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw BusinessException.of(401, MSG_TOKEN_TYPE_INVALID);
        }

        Long userId = Long.parseLong(claims.getSubject());
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BusinessException.of(401, MSG_USER_MISSING_OR_DISABLED);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRoleType()));
        result.put("expiresIn", jwtUtil.getAccessTokenExpire());
        return R.ok(MSG_REFRESH_SUCCESS, result);
    }

    @Log("\u627e\u56de\u5bc6\u7801\u7533\u8bf7")
    @PostMapping("/password/reset/request")
    public R<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        captchaService.verify(req.getCaptchaId(), req.getCaptchaCode());

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (user == null || !StringUtils.hasText(user.getEmail())
                || !user.getEmail().trim().equalsIgnoreCase(req.getEmail().trim())) {
            throw BusinessException.of(400, MSG_RESET_IDENTITY_MISMATCH);
        }
        if (!authMailService.isMailAvailable()) {
            throw BusinessException.of(503, MSG_MAIL_DISABLED);
        }

        Map<String, Object> result = new HashMap<>(passwordResetService.issueEmailCode(user.getId(), user.getUsername(), user.getEmail()));
        authMailService.sendPasswordResetCode(
                user.getEmail(),
                user.getUsername(),
                passwordResetService.peekCode(req.getUsername(), req.getEmail()),
                passwordResetService.getCodeTtl()
        );
        result.put("maskedEmail", maskEmail(user.getEmail()));
        return R.ok(MSG_MAIL_CODE_SENT, result);
    }

    @Log("\u91cd\u7f6e\u5bc6\u7801")
    @PostMapping("/password/reset/confirm")
    public R<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        validatePasswordStrength(req.getNewPassword());
        Long userId = passwordResetService.verifyEmailCode(req.getUsername(), req.getEmail(), req.getEmailCode());

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound(MSG_USER_NOT_FOUND);
        }
        if (!user.getUsername().trim().equalsIgnoreCase(req.getUsername().trim())
                || !StringUtils.hasText(user.getEmail())
                || !user.getEmail().trim().equalsIgnoreCase(req.getEmail().trim())) {
            throw BusinessException.of(400, MSG_RESET_VERIFY_MISMATCH);
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return R.ok(MSG_RESET_SUCCESS, null);
    }

    @Log("\u4fee\u6539\u5bc6\u7801")
    @PutMapping("/password")
    public R<?> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound(MSG_USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw BusinessException.of(400, MSG_OLD_PASSWORD_INVALID);
        }

        validatePasswordStrength(req.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return R.ok(MSG_CHANGE_PASSWORD_SUCCESS, null);
    }

    @GetMapping("/profile")
    public R<?> getProfile() {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound(MSG_USER_NOT_FOUND);
        }

        Map<String, Object> profile = buildUserPayload(user);
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("lastLoginAt", user.getLastLoginAt());
        profile.put("createdAt", user.getCreatedAt());
        return R.ok(profile);
    }

    @Log("\u66f4\u65b0\u4e2a\u4eba\u4fe1\u606f")
    @PutMapping("/profile")
    public R<?> updateProfile(@RequestBody UpdateProfileRequest req) {
        Long userId = getCurrentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound(MSG_USER_NOT_FOUND);
        }

        user.setNickname(normalizeProfileField(req.getNickname()));
        user.setEmail(normalizeProfileField(req.getEmail()));
        user.setPhone(normalizeProfileField(req.getPhone()));
        user.setAvatarUrl(normalizeProfileField(req.getAvatarUrl()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return R.ok(MSG_UPDATE_SUCCESS, null);
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
            throw BusinessException.unauthorized(MSG_LOGIN_REQUIRED);
        }
        return (Long) authentication.getPrincipal();
    }

    private void validatePasswordStrength(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw BusinessException.of(400, MSG_PASSWORD_TOO_SHORT);
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw BusinessException.of(400, MSG_PASSWORD_RULE_INVALID);
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

    private String normalizeProfileField(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
