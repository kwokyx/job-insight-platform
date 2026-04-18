package com.career.platform.open.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.open.entity.ApiKey;
import com.career.platform.open.mapper.ApiKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyMapper apiKeyMapper;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate) {
        this.apiKeyMapper = apiKeyMapper;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ApiKey createApiKey(Long userId, String keyName, int rateLimitQps, int dailyQuota) {
        ApiKey key = new ApiKey();
        key.setUserId(userId);
        key.setApiKey("cpk_" + UUID.randomUUID().toString().replace("-", ""));
        key.setKeyName(keyName);
        key.setRateLimitQps(rateLimitQps);
        key.setDailyQuota(dailyQuota);
        key.setIsActive(1);
        key.setCreatedAt(LocalDateTime.now());
        apiKeyMapper.insert(key);
        return key;
    }

    public ApiKey findActiveKey(String rawKey) {
        return apiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getApiKey, rawKey)
                        .eq(ApiKey::getIsActive, 1)
        );
    }

    public ApiKey validateAndTrack(String rawKey) {
        ApiKey key = findActiveKey(rawKey);

        if (key == null) {
            throw BusinessException.of(401, "Invalid or disabled API Key");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.of(401, "API Key expired");
        }
        int dailyQuota = key.getDailyQuota() == null ? 1000 : key.getDailyQuota();
        int rateLimitQps = key.getRateLimitQps() == null ? 10 : key.getRateLimitQps();
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping API key rate limit tracking for key {}", key.getId());
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyMapper.updateById(key);
            return key;
        }

        String quotaKey = "api:quota:" + key.getId() + ":" + LocalDate.now();
        String used = redisTemplate.opsForValue().get(quotaKey);
        int usedCount = used != null ? Integer.parseInt(used) : 0;
        if (usedCount >= dailyQuota) {
            throw BusinessException.of(429, "API daily quota exceeded (" + dailyQuota + ")");
        }

        String qpsKey = "api:qps:" + key.getId() + ":" + (System.currentTimeMillis() / 1000);
        Long currentQps = redisTemplate.opsForValue().increment(qpsKey);
        redisTemplate.expire(qpsKey, 2, TimeUnit.SECONDS);
        if (currentQps != null && currentQps > rateLimitQps) {
            throw BusinessException.of(429, "API QPS exceeded (" + rateLimitQps + ")");
        }

        redisTemplate.opsForValue().increment(quotaKey);
        redisTemplate.expire(quotaKey, 25, TimeUnit.HOURS);

        key.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(key);
        return key;
    }

    public void enforceBurstLimit(String rawKey) {
        ApiKey key = findActiveKey(rawKey);
        if (key == null) {
            throw BusinessException.of(401, "Invalid or disabled API Key");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.of(401, "API Key expired");
        }

        int rateLimitQps = key.getRateLimitQps() == null ? 10 : key.getRateLimitQps();
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping API key burst limit for key {}", key.getId());
            return;
        }
        String tokenKey = "api:bucket:" + key.getId();
        String timestampKey = tokenKey + ":ts";
        long now = System.currentTimeMillis();
        long intervalMs = 1000L;

        String storedTokens = redisTemplate.opsForValue().get(tokenKey);
        String storedTimestamp = redisTemplate.opsForValue().get(timestampKey);

        double tokens = storedTokens == null ? rateLimitQps : Double.parseDouble(storedTokens);
        long lastTs = storedTimestamp == null ? now : Long.parseLong(storedTimestamp);

        double refill = ((double) Math.max(0, now - lastTs) / intervalMs) * rateLimitQps;
        tokens = Math.min(rateLimitQps, tokens + refill);
        if (tokens < 1.0d) {
            throw BusinessException.of(429, "API QPS exceeded (" + rateLimitQps + ")");
        }

        tokens = tokens - 1.0d;
        redisTemplate.opsForValue().set(tokenKey, String.format("%.4f", tokens), 2, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(timestampKey, String.valueOf(now), 2, TimeUnit.SECONDS);
    }

    public int getRemainingQuota(ApiKey key) {
        int dailyQuota = key.getDailyQuota() == null ? 1000 : key.getDailyQuota();
        if (!isRedisAvailable()) {
            return dailyQuota;
        }
        String quotaKey = "api:quota:" + key.getId() + ":" + LocalDate.now();
        String used = redisTemplate.opsForValue().get(quotaKey);
        int usedCount = used != null ? Integer.parseInt(used) : 0;
        return Math.max(0, dailyQuota - usedCount);
    }

    public void recordApiCall(ApiKey key,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              long durationMs) {
        jdbcTemplate.update(
                "INSERT INTO sys_api_call_log (api_key_id, endpoint, method, request_params, response_code, response_time, ip_address, user_agent, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                key.getId(),
                request.getRequestURI(),
                request.getMethod(),
                request.getQueryString(),
                response.getStatus(),
                durationMs,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }

    private boolean isRedisAvailable() {
        try {
            redisTemplate.hasKey("health:redis");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
