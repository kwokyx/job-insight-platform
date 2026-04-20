package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int CAPTCHA_THRESHOLD = 3;
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:login:fail:";

    private final StringRedisTemplate redisTemplate;
    private final Map<String, AttemptState> localAttempts = new ConcurrentHashMap<>();

    public LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkAllowed(String username) {
        String normalized = normalize(username);
        if (!StringUtils.hasText(normalized)) {
            return;
        }

        if (getFailures(normalized) >= MAX_FAILED_ATTEMPTS) {
            throw BusinessException.of(429, "登录失败次数过多，请 15 分钟后重试");
        }
    }

    public void onSuccess(String username) {
        String normalized = normalize(username);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        deleteCounter(normalized);
    }

    public void onFailure(String username) {
        String normalized = normalize(username);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        incrementCounter(normalized);
    }

    public boolean requiresCaptcha(String username) {
        String normalized = normalize(username);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return getFailures(normalized) >= CAPTCHA_THRESHOLD;
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private int getFailures(String username) {
        if (redisTemplate == null) {
            return getLocalFailures(username);
        }
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + username);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception ignored) {
            return getLocalFailures(username);
        }
    }

    private void incrementCounter(String username) {
        if (redisTemplate == null) {
            incrementLocalCounter(username);
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(KEY_PREFIX + username);
            if (count != null && count == 1L) {
                redisTemplate.expire(KEY_PREFIX + username, LOCK_WINDOW);
            }
        } catch (Exception ignored) {
            incrementLocalCounter(username);
        }
    }

    private void deleteCounter(String username) {
        if (redisTemplate == null) {
            localAttempts.remove(username);
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + username);
        } catch (Exception ignored) {
            localAttempts.remove(username);
        }
    }

    private int getLocalFailures(String username) {
        AttemptState state = localAttempts.get(username);
        if (state == null || state.expiresAt < System.currentTimeMillis()) {
            localAttempts.remove(username);
            return 0;
        }
        return state.count;
    }

    private void incrementLocalCounter(String username) {
        long now = System.currentTimeMillis();
        AttemptState current = localAttempts.get(username);
        if (current == null || current.expiresAt < now) {
            current = new AttemptState();
            current.count = 0;
            current.expiresAt = now + LOCK_WINDOW.toMillis();
        }
        current.count += 1;
        localAttempts.put(username, current);
    }

    private static class AttemptState {
        private int count;
        private long expiresAt;
    }
}
