package com.career.platform.config;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 启动时缓存预热任务
 * 服务启动后异步预热 L1/L2 核心缓存，确保首批请求直接命中 Redis（50ms 响应）
 * 而非等待 DB 查询（300-3000ms 响应）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmUpTask implements ApplicationRunner {

    private final JobPostingMapper jobMapper;
    private final MarketSkillService marketSkillService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Executor taskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        // 异步预热，不阻塞 Spring 启动
        CompletableFuture.runAsync(this::warmUp, taskExecutor)
                .exceptionally(e -> {
                    log.warn("缓存预热失败（非致命），服务正常启动: {}", e.getMessage());
                    return null;
                });
    }

    private void warmUp() {
        log.info("[缓存预热] 开始预热核心缓存...");
        long start = System.currentTimeMillis();

        // L1 热点 — 首页 overview（10min TTL）
        safeWarmUp("cache:analysis:overview", this::buildOverview, 10, TimeUnit.MINUTES);

        // L2 维度 — 技能排行（30min TTL）
        safeWarmUp("cache:topSkills:20", () -> marketSkillService.topSkills(20), 30, TimeUnit.MINUTES);

        // L2 维度 — 城市分布（30min TTL）
        safeWarmUp("cache:topCities:10", () -> jobMapper.aggregateByCity(10), 30, TimeUnit.MINUTES);

        // L2 维度 — 学历分布（30min TTL）
        safeWarmUp("cache:educationDist", jobMapper::aggregateByEducation, 30, TimeUnit.MINUTES);

        log.info("[缓存预热] 完成，耗时 {} ms", System.currentTimeMillis() - start);
    }

    private Map<String, Object> buildOverview() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> stats = jobMapper.overviewStats();
        data.put("totalJobs", jobMapper.selectCount(null));
        data.put("avgSalaryMin", stats.get("avgSalaryMin"));
        data.put("avgSalaryMax", stats.get("avgSalaryMax"));
        data.put("topCities", jobMapper.aggregateByCity(10));
        data.put("topIndustries", jobMapper.aggregateByIndustry(10));
        data.put("topSkills", jobMapper.topSkills(10));
        data.put("educationDistribution", jobMapper.aggregateByEducation());
        data.put("experienceDistribution", jobMapper.aggregateByExperience());
        return data;
    }

    private void safeWarmUp(String key, Supplier<Object> dataSupplier, long timeout, TimeUnit unit) {
        try {
            // 仅在缓存不存在时预热，避免覆盖新鲜数据
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                log.debug("[缓存预热] {} 已存在，跳过", key);
                return;
            }
            Object data = dataSupplier.get();
            redisTemplate.opsForValue().set(key, data, timeout, unit);
            log.info("[缓存预热] {} 写入成功", key);
        } catch (Exception e) {
            log.warn("[缓存预热] {} 预热失败: {}", key, e.getMessage());
        }
    }
}
