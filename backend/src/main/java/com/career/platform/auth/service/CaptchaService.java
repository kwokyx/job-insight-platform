package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        return createCaptcha("AUTO");
    }

    public Map<String, Object> createCaptcha(String preferredType) {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String normalizedType = normalizeType(preferredType);
        String answer;
        String prompt;

        if ("CHAR".equals(normalizedType)) {
            String token = randomAlphaNumeric(4);
            answer = token.toLowerCase(Locale.ROOT);
            prompt = buildCharPrompt(token);
        } else {
            int left = ThreadLocalRandom.current().nextInt(1, 10);
            int right = ThreadLocalRandom.current().nextInt(1, 10);
            answer = String.valueOf(left + right);
            prompt = left + " + " + right + " = ?";
            normalizedType = "MATH";
        }

        storeAnswer(captchaId, answer);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("captchaPrompt", prompt);
        result.put("captchaType", normalizedType);
        result.put("expiresInSeconds", CAPTCHA_TTL.getSeconds());
        return result;
    }

    public void verify(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw BusinessException.of(400, "请输入验证码");
        }

        String normalizedId = captchaId.trim();
        String expected = getAnswer(normalizedId);
        if (!StringUtils.hasText(expected)) {
            throw BusinessException.of(400, "验证码已失效，请重新获取");
        }

        String normalizedCode = captchaCode.trim().toLowerCase(Locale.ROOT);
        if (!expected.equals(normalizedCode)) {
            deleteAnswer(normalizedId);
            throw BusinessException.of(400, "验证码错误，请重新获取");
        }

        deleteAnswer(normalizedId);
    }

    private void storeAnswer(String captchaId, String answer) {
        String normalized = answer.toLowerCase(Locale.ROOT);
        if (redisTemplate == null) {
            localCaptchaStore.put(captchaId, new LocalCaptchaState(normalized, System.currentTimeMillis() + CAPTCHA_TTL.toMillis()));
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, normalized, CAPTCHA_TTL);
        } catch (Exception ignored) {
            localCaptchaStore.put(captchaId, new LocalCaptchaState(normalized, System.currentTimeMillis() + CAPTCHA_TTL.toMillis()));
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

    private String normalizeType(String preferredType) {
        String normalized = preferredType == null ? "" : preferredType.trim().toUpperCase(Locale.ROOT);
        if ("CHAR".equals(normalized) || "TEXT".equals(normalized)) {
            return "CHAR";
        }
        if ("MATH".equals(normalized)) {
            return "MATH";
        }
        return ThreadLocalRandom.current().nextBoolean() ? "MATH" : "CHAR";
    }

    private String randomAlphaNumeric(int length) {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet[ThreadLocalRandom.current().nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    private String buildCharPrompt(String token) {
        List<String> chars = new ArrayList<>();
        for (char ch : token.toCharArray()) {
            chars.add(String.valueOf(ch));
        }
        return "请输入字符验证码: " + String.join(" ", chars);
    }

    private static class LocalCaptchaState {
        private final String answer;
        private final long expiresAt;

        private LocalCaptchaState(String answer, long expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }
    }
}
