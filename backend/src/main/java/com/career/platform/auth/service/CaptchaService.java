package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaService {

    private static final String KEY_PREFIX = "auth:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalCaptchaState> localCaptchaStore = new ConcurrentHashMap<>();

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> createCaptcha() {
        int left = ThreadLocalRandom.current().nextInt(1, 10);
        int right = ThreadLocalRandom.current().nextInt(1, 10);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String answer = String.valueOf(left + right);

        storeAnswer(captchaId, answer);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("captchaPrompt", left + " + " + right + " = ?");
        result.put("expiresInSeconds", CAPTCHA_TTL.getSeconds());
        return result;
    }

    public void verify(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw BusinessException.of(400, "请输入验证码");
        }

        String expected = getAnswer(captchaId.trim());
        if (!StringUtils.hasText(expected)) {
            throw BusinessException.of(400, "验证码已失效，请重新获取");
        }

        String normalized = captchaCode.trim().toLowerCase(Locale.ROOT);
        if (!expected.equals(normalized)) {
            deleteAnswer(captchaId.trim());
            throw BusinessException.of(400, "验证码错误，请重新获取");
        }

        deleteAnswer(captchaId.trim());
    }

    private void storeAnswer(String captchaId, String answer) {
        if (redisTemplate == null) {
            localCaptchaStore.put(captchaId, new LocalCaptchaState(answer, System.currentTimeMillis() + CAPTCHA_TTL.toMillis()));
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, answer.toLowerCase(Locale.ROOT), CAPTCHA_TTL);
        } catch (Exception ignored) {
            localCaptchaStore.put(captchaId, new LocalCaptchaState(answer, System.currentTimeMillis() + CAPTCHA_TTL.toMillis()));
        }
    }

    private String getAnswer(String captchaId) {
        if (redisTemplate == null) {
            return getLocalAnswer(captchaId);
        }
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + captchaId);
        } catch (Exception ignored) {
            return getLocalAnswer(captchaId);
        }
    }

    private void deleteAnswer(String captchaId) {
        if (redisTemplate == null) {
            localCaptchaStore.remove(captchaId);
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + captchaId);
        } catch (Exception ignored) {
            localCaptchaStore.remove(captchaId);
        }
    }

    private String getLocalAnswer(String captchaId) {
        LocalCaptchaState state = localCaptchaStore.get(captchaId);
        if (state == null || state.expiresAt < System.currentTimeMillis()) {
            localCaptchaStore.remove(captchaId);
            return null;
        }
        return state.answer;
    }

    private static class LocalCaptchaState {
        private final String answer;
        private final long expiresAt;

        private LocalCaptchaState(String answer, long expiresAt) {
            this.answer = answer.toLowerCase(Locale.ROOT);
            this.expiresAt = expiresAt;
        }
    }
}
