package com.career.platform.auth.controller;

import com.career.platform.auth.service.CaptchaService;
import com.career.platform.auth.service.AuthThrottleService;
import com.career.platform.auth.service.LoginAttemptService;
import com.career.platform.auth.service.PasswordResetService;
import com.career.platform.auth.util.JwtUtil;
import com.career.platform.common.exception.GlobalExceptionHandler;
import com.career.platform.system.entity.SysUser;
import com.career.platform.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private SysUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private CaptchaService captchaService;
    private AuthThrottleService authThrottleService;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        loginAttemptService = mock(LoginAttemptService.class);
        captchaService = mock(CaptchaService.class);
        authThrottleService = mock(AuthThrottleService.class);
        passwordResetService = mock(PasswordResetService.class);

        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpire", 7200L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpire", 259200L);

        AuthController controller = new AuthController(
                userMapper, jwtUtil, passwordEncoder, loginAttemptService, captchaService, authThrottleService, passwordResetService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerCreatesUser() throws Exception {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret123");
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(11L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        String payload = "{"
                + "\"username\":\"alice\","
                + "\"password\":\"secret123\","
                + "\"email\":\"alice@example.com\","
                + "\"nickname\":\"Alice\","
                + "\"captchaId\":\"cid123\","
                + "\"captchaCode\":\"8\""
                + "}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.userId").value(11));

        verify(captchaService).verify("cid123", "8");
    }

    @Test
    void registerRejectsWeakPassword() throws Exception {
        String payload = "{"
                + "\"username\":\"weak-user\","
                + "\"password\":\"weak\","
                + "\"email\":\"weak@example.com\","
                + "\"captchaId\":\"cid456\","
                + "\"captchaCode\":\"6\""
                + "}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度至少 8 位"));

        verify(captchaService).verify("cid456", "6");
    }

    @Test
    void loginReturnsTokensAndUserProfile() throws Exception {
        SysUser user = new SysUser();
        user.setId(3L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPasswordHash("encoded");
        user.setRoleType(0);
        user.setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "encoded")).thenReturn(true);
        when(loginAttemptService.requiresCaptcha("alice")).thenReturn(false);

        String payload = "{"
                + "\"username\":\"alice\","
                + "\"password\":\"secret123\""
                + "}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.id").value(3))
                .andExpect(jsonPath("$.data.user.username").value("alice"));

        verify(loginAttemptService).checkAllowed("alice");
        verify(loginAttemptService).onSuccess("alice");
        verify(userMapper).updateById(any(SysUser.class));
    }

    @Test
    void loginVerifiesCaptchaWhenRiskEscalates() throws Exception {
        SysUser user = new SysUser();
        user.setId(5L);
        user.setUsername("risk-user");
        user.setPasswordHash("encoded");
        user.setRoleType(0);
        user.setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", "encoded")).thenReturn(true);
        when(loginAttemptService.requiresCaptcha("risk-user")).thenReturn(true);

        String payload = "{"
                + "\"username\":\"risk-user\","
                + "\"password\":\"secret123\","
                + "\"captchaId\":\"cid123\","
                + "\"captchaCode\":\"8\""
                + "}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(captchaService).verify("cid123", "8");
    }

    @Test
    void updateProfileUpdatesCurrentUser() throws Exception {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("bob");
        when(userMapper.selectById(8L)).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(8L, 0));

        String payload = "{"
                + "\"nickname\":\"Bobby\","
                + "\"email\":\"bob@example.com\","
                + "\"phone\":\"13800000000\""
                + "}";

        mockMvc.perform(put("/api/v1/auth/profile")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("更新成功"));

        verify(userMapper).updateById(any(SysUser.class));
    }

    @Test
    void getProfileReturnsNotFoundWhenUserMissing() throws Exception {
        when(userMapper.selectById(99L)).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(99L, 0));

        mockMvc.perform(get("/api/v1/auth/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void requestPasswordResetReturnsResetToken() throws Exception {
        SysUser user = new SysUser();
        user.setId(22L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordResetService.issueResetToken(22L, "alice")).thenReturn(new java.util.HashMap<String, Object>() {{
            put("resetToken", "reset-123");
            put("expiresInSeconds", 900L);
            put("username", "alice");
        }});

        String payload = "{"
                + "\"username\":\"alice\","
                + "\"email\":\"alice@example.com\","
                + "\"captchaId\":\"cid999\","
                + "\"captchaCode\":\"6\""
                + "}";

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.resetToken").value("reset-123"))
                .andExpect(jsonPath("$.data.maskedEmail").value("al***@example.com"));
    }

    @Test
    void confirmPasswordResetUpdatesPassword() throws Exception {
        SysUser user = new SysUser();
        user.setId(22L);
        when(passwordResetService.consumeResetToken("reset-123")).thenReturn(22L);
        when(userMapper.selectById(22L)).thenReturn(user);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret123");

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"resetToken\":\"reset-123\",\"newPassword\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码重置成功"));

        verify(userMapper).updateById(any(SysUser.class));
    }
}
