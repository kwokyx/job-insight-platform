package com.career.platform.job.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 职位数据控制器
 */
@Tag(name = "职位数据", description = "职位查询、搜索、聚合统计")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobPostingMapper jobMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─── 职位列表（分页+筛选）──────────────

    @Operation(summary = "职位列表（分页+多条件筛选）")
    @GetMapping
    public R<?> listJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) Double salaryMin,
            @RequestParam(required = false) Double salaryMax,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "publish_date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
        ) {
        LambdaQueryWrapper<JobPosting> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(JobPosting::getTitle, keyword)
                    .or().like(JobPosting::getCompanyName, keyword)
                    .or().like(JobPosting::getDescription, keyword)
            );
        }
        if (StringUtils.hasText(city)) {
            wrapper.like(JobPosting::getCity, city);
        }
        if (StringUtils.hasText(industry)) {
            wrapper.like(JobPosting::getIndustryName, industry);
        }
        if (StringUtils.hasText(education)) {
            wrapper.eq(JobPosting::getEducation, education);
        }
        if (StringUtils.hasText(experience)) {
            wrapper.like(JobPosting::getExperience, experience);
        }
        if (salaryMin != null) {
            wrapper.ge(JobPosting::getSalaryMin, salaryMin);
        }
        if (salaryMax != null) {
            wrapper.le(JobPosting::getSalaryMax, salaryMax);
        }

        // 排序
        if ("asc".equalsIgnoreCase(sortOrder)) {
            wrapper.orderByAsc(JobPosting::getPublishDate);
        } else {
            wrapper.orderByDesc(JobPosting::getPublishDate);
        }

        // 分页查询
        IPage<JobPosting> result = jobMapper.selectPage(
                new Page<>(page, Math.min(pageSize, 100)),
                wrapper
        );

        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    // ─── 职位详情 ────────────────────────

    @Operation(summary = "职位详情")
    @GetMapping("/{id}")
    public R<JobPosting> getJob(@PathVariable Long id) {
        JobPosting job = jobMapper.selectById(id);
        if (job == null) {
            throw BusinessException.notFound("职位不存在");
        }
        return R.ok(job);
    }

    // ─── 职位统计总览 ────────────────────

    @Operation(summary = "职位统计总览")
    @GetMapping("/stats")
    public R<?> getStats() {
        Map<String, Object> overview = jobMapper.overviewStats();
        long totalJobs = jobMapper.selectCount(null);
        overview.put("totalJobs", totalJobs);
        return R.ok(overview);
    }

    // ─── 按城市聚合 ─────────────────────

    @Operation(summary = "按城市聚合统计")
    @GetMapping("/by-city")
    public R<List<Map<String, Object>>> byCity(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return R.ok(jobMapper.aggregateByCity(limit));
    }

    // ─── 按行业聚合 ─────────────────────

    @Operation(summary = "按行业聚合统计")
    @GetMapping("/by-industry")
    public R<List<Map<String, Object>>> byIndustry(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return R.ok(jobMapper.aggregateByIndustry(limit));
    }

    // ─── 按学历聚合 ─────────────────────

    @Operation(summary = "按学历聚合统计")
    @GetMapping("/by-education")
    public R<List<Map<String, Object>>> byEducation() {
        return R.ok(jobMapper.aggregateByEducation());
    }

    // ─── 按经验聚合 ─────────────────────

    @Operation(summary = "按经验聚合统计")
    @GetMapping("/by-experience")
    public R<List<Map<String, Object>>> byExperience() {
        return R.ok(jobMapper.aggregateByExperience());
    }

    @Operation(summary = "职位全文搜索")
    @GetMapping("/search")
    public R<?> searchJobs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        if (!StringUtils.hasText(keyword)) {
            return R.badRequest("keyword 不能为空");
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        String plainKeyword = keyword.trim();
        String booleanKeyword = buildBooleanKeyword(plainKeyword);
        long offset = (long) (safePage - 1) * safePageSize;

        List<Map<String, Object>> records = jobMapper.searchJobs(booleanKeyword, plainKeyword, offset, safePageSize);
        long total = jobMapper.countSearchJobs(booleanKeyword, plainKeyword);

        Map<String, Object> aggregations = new HashMap<>();
        aggregations.put("cities", jobMapper.searchAggregateByCity(booleanKeyword, plainKeyword));
        aggregations.put("industries", jobMapper.searchAggregateByIndustry(booleanKeyword, plainKeyword));
        aggregations.put("education", jobMapper.searchAggregateByEducation(booleanKeyword, plainKeyword));
        aggregations.put("experience", jobMapper.searchAggregateByExperience(booleanKeyword, plainKeyword));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", plainKeyword);
        payload.put("records", records);
        payload.put("aggregations", aggregations);

        R<Map<String, Object>> result = R.ok(payload);
        result.setTotal(total);
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        return result;
    }

    @Operation(summary = "热门职位 TOP-N")
    @SuppressWarnings("unchecked")
    @GetMapping("/hot")
    public R<?> hotJobs(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String cacheKey = "cache:jobs:hot:" + safeLimit;
        Object cached = safeGet(cacheKey);
        if (cached != null) {
            return R.ok(cached);
        }

        List<Map<String, Object>> jobs = jobMapper.hotJobs(safeLimit);
        safeSet(cacheKey, jobs, 1, TimeUnit.HOURS);
        return R.ok(jobs);
    }

    private String buildBooleanKeyword(String keyword) {
        String[] parts = keyword.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append('+').append(part).append('*');
        }
        return builder.length() == 0 ? keyword : builder.toString();
    }

    private Object safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void safeSet(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis write failed for key {}: {}", key, e.getMessage());
        }
    }
}
