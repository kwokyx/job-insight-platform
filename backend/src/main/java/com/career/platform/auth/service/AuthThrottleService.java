package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthThrottleService {

    private static final String REGISTER_KEY_PREFIX = "auth:throttle:register:";
    private static final String LOGIN_KEY_PREFIX = "auth:throttle:login:";
    private static final int REGISTER_LIMIT = 6;
    private static final int LOGIN_IP_LIMIT = 18;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, CounterState> localCounters = new ConcurrentHashMap<>();

    public AuthThrottleService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkRegisterAllowed(HttpServletRequest request) {
        enforce(REGISTER_KEY_PREFIX, clientFingerprint(request), REGISTER_LIMIT, REGISTER_WINDOW,
                "当前 IP 注册过于频繁，请 1 小时后再试");
    }

    public void onRegisterFailure(HttpServletRequest request) {
        increment(REGISTER_KEY_PREFIX, clientFingerprint(request), REGISTER_WINDOW);
    }

    public void onRegisterSuccess(HttpServletRequest request) {
        clear(REGISTER_KEY_PREFIX, clientFingerprint(request));
    }

    public void checkLoginAllowed(HttpServletRequest request) {
        enforce(LOGIN_KEY_PREFIX, clientFingerprint(request), LOGIN_IP_LIMIT, LOGIN_WINDOW,
                "当前 IP 登录尝试过于频繁，请 15 分钟后再试");
    }

    public void onLoginFailure(HttpServletRequest request) {
        increment(LOGIN_KEY_PREFIX, clientFingerprint(request), LOGIN_WINDOW);
    }

    public void onLoginSuccess(HttpServletRequest request) {
        clear(LOGIN_KEY_PREFIX, clientFingerprint(request));
    }

    private void enforce(String prefix, String fingerprint, int limit, Duration window, String message) {
        if (!StringUtils.hasText(fingerprint)) {
            return;
        }
        if (getCount(prefix + fingerprint) >= limit) {
            throw BusinessException.of(429, message);
        }
    }

    private void increment(String prefix, String fingerprint, Duration window) {
        if (!StringUtils.hasText(fingerprint)) {
            return;
        }
        String key = prefix + fingerprint;
        if (redisTemplate == null) {
            incrementLocal(key, window);
            return;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
        } catch (Exception ignored) {
            incrementLocal(key, window);
        }
    }

    private void clear(String prefix, String fingerprint) {
        if (!StringUtils.hasText(fingerprint)) {
            return;
        }
        String key = prefix + fingerprint;
        if (redisTemplate == null) {
            localCounters.remove(key);
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
            localCounters.remove(key);
        }
    }

    private int getCount(String key) {
        if (redisTemplate == null) {
            return getLocalCount(key);
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception ignored) {
            return getLocalCount(key);
        }
    }

    private int getLocalCount(String key) {
        CounterState state = localCounters.get(key);
        if (state == null || state.expiresAt < System.currentTimeMillis()) {
            localCounters.remove(key);
            return 0;
        }
        return state.count;
    }

    private void incrementLocal(String key, Duration window) {
        long now = System.currentTimeMillis();
        CounterState state = localCounters.get(key);
        if (state == null || state.expiresAt < now) {
            state = new CounterState();
            state.count = 0;
            state.expiresAt = now + window.toMillis();
        }
        state.count += 1;
        localCounters.put(key, state);
    }

    private String clientFingerprint(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = firstHeader(request, "X-Forwarded-For");
        String realIp = firstHeader(request, "X-Real-IP");
        String remote = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim()
                : StringUtils.hasText(realIp) ? realIp.trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return (remote == null ? "" : remote.trim()) + "|" + (userAgent == null ? "na" : userAgent.trim());
    }

    private String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value;
    }

    private static class CounterState {
        private int count;
        private long expiresAt;
    }
}
