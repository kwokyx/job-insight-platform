package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetService {

    private static final String KEY_PREFIX = "auth:password-reset:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, ResetState> localStore = new ConcurrentHashMap<>();

    public PasswordResetService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> issueResetToken(Long userId, String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String payload = userId + "|" + (username == null ? "" : username.trim());
        store(token, payload);
        return new java.util.LinkedHashMap<String, Object>() {{
            put("resetToken", token);
            put("expiresInSeconds", TOKEN_TTL.getSeconds());
            put("username", username);
        }};
    }

    public Long consumeResetToken(String resetToken) {
        if (!StringUtils.hasText(resetToken)) {
            throw BusinessException.of(400, "重置令牌不能为空");
        }
        String payload = get(resetToken.trim());
        if (!StringUtils.hasText(payload)) {
            throw BusinessException.of(400, "重置令牌已失效，请重新发起找回密码");
        }
        delete(resetToken.trim());
        String[] parts = payload.split("\\|", 2);
        if (parts.length == 0) {
            throw BusinessException.of(400, "重置令牌无效");
        }
        return Long.parseLong(parts[0]);
    }

    private void store(String token, String payload) {
        if (redisTemplate == null) {
            localStore.put(token, new ResetState(payload, System.currentTimeMillis() + TOKEN_TTL.toMillis()));
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + token, payload, TOKEN_TTL);
        } catch (Exception ignored) {
            localStore.put(token, new ResetState(payload, System.currentTimeMillis() + TOKEN_TTL.toMillis()));
        }
    }

    private String get(String token) {
        if (redisTemplate == null) {
            return getLocal(token);
        }
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + token);
        } catch (Exception ignored) {
            return getLocal(token);
        }
    }

    private void delete(String token) {
        if (redisTemplate == null) {
            localStore.remove(token);
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + token);
        } catch (Exception ignored) {
            localStore.remove(token);
        }
    }

    private String getLocal(String token) {
        ResetState state = localStore.get(token);
        if (state == null || state.expiresAt < System.currentTimeMillis()) {
            localStore.remove(token);
            return null;
        }
        return state.payload;
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
