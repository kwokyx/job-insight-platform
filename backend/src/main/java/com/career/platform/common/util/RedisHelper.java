package com.career.platform.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类 — 统一处理 null check 和异常捕获
 * 替代各 Controller/Service 中散落的重复 try-catch Redis 包装代码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHelper {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 安全读取缓存，失败返回 null
     */
    public Object safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 安全写入缓存，失败静默忽略
     */
    public void safeSet(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis write failed for key {}: {}", key, e.getMessage());
        }
    }

    /**
     * 安全自增计数器，失败返回 null
     */
    public Long safeIncr(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("Redis incr failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 安全设置过期时间
     */
    public void safeExpire(String key, long timeout, TimeUnit unit) {
        try {
            redisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis expire failed for key {}: {}", key, e.getMessage());
        }
    }

    /**
     * 安全删除 key
     */
    public void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete failed for key {}: {}", key, e.getMessage());
        }
    }

    /**
     * 读取字符串 key，失败返回 null
     */
    public String safeGetString(String key) {
        try {
            Object val = redisTemplate.opsForValue().get(key);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            log.warn("Redis getString failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }
}
