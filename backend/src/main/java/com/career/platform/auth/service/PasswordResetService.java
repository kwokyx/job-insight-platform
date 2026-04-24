package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PasswordResetService {

    private static final String KEY_PREFIX = "auth:password-reset:";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, ResetState> localStore = new ConcurrentHashMap<>();

    public PasswordResetService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> issueEmailCode(Long userId, String username, String email) {
        if (userId == null || !StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw BusinessException.of(400, "缺少找回密码所需信息");
        }

        String code = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        String payload = userId + "|" + normalize(username) + "|" + normalizeEmail(email) + "|" + code;
        store(identityKey(username, email), payload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expiresInSeconds", CODE_TTL.getSeconds());
        result.put("username", username.trim());
        return result;
    }

    public Long verifyEmailCode(String username, String email, String emailCode) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw BusinessException.of(400, "用户名和邮箱不能为空");
        }
        if (!StringUtils.hasText(emailCode)) {
            throw BusinessException.of(400, "邮箱验证码不能为空");
        }

        String payload = get(identityKey(username, email));
        if (!StringUtils.hasText(payload)) {
            throw BusinessException.of(400, "邮箱验证码已失效，请重新发送");
        }

        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4) {
            delete(identityKey(username, email));
            throw BusinessException.of(400, "邮箱验证码无效");
        }

        if (!parts[3].equals(emailCode.trim())) {
            throw BusinessException.of(400, "邮箱验证码错误");
        }

        delete(identityKey(username, email));
        return Long.parseLong(parts[0]);
    }

    public String peekCode(String username, String email) {
        String payload = get(identityKey(username, email));
        if (!StringUtils.hasText(payload)) {
            throw BusinessException.of(400, "邮箱验证码已失效，请重新发送");
        }

        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4) {
            throw BusinessException.of(400, "邮箱验证码无效");
        }
        return parts[3];
    }

    public Duration getCodeTtl() {
        return CODE_TTL;
    }

    private void store(String key, String payload) {
        if (redisTemplate == null) {
            localStore.put(key, new ResetState(payload, System.currentTimeMillis() + CODE_TTL.toMillis()));
            return;
        }

        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + key, payload, CODE_TTL);
        } catch (Exception ignored) {
            localStore.put(key, new ResetState(payload, System.currentTimeMillis() + CODE_TTL.toMillis()));
        }
    }

    private String get(String key) {
        if (redisTemplate == null) {
            return getLocal(key);
        }

        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception ignored) {
            return getLocal(key);
        }
    }

    private void delete(String key) {
        if (redisTemplate == null) {
            localStore.remove(key);
            return;
        }

        try {
            redisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception ignored) {
            localStore.remove(key);
        }
    }

    private String getLocal(String key) {
        ResetState state = localStore.get(key);
        if (state == null || state.expiresAt < System.currentTimeMillis()) {
            localStore.remove(key);
            return null;
        }
        return state.payload;
    }

    private String identityKey(String username, String email) {
        return normalize(username) + "|" + normalizeEmail(email);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return normalize(email);
    }

    private static class ResetState {
        private final String payload;
        private final long expiresAt;

        private ResetState(String payload, long expiresAt) {
            this.payload = payload;
            this.expiresAt = expiresAt;
        }
    }
}
