package com.career.platform.job.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.job.entity.JobFavorite;
import com.career.platform.job.mapper.JobFavoriteMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位收藏控制器
 */
@Tag(name = "岗位收藏", description = "收藏/取消收藏岗位")
@RestController
@RequestMapping("/api/v1/favorites")
public class JobFavoriteController {

    private final JobFavoriteMapper favoriteMapper;

    public JobFavoriteController(JobFavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping
    public R<?> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        long total = favoriteMapper.countByUser(userId);
        List<Map<String, Object>> items = favoriteMapper.userFavorites(userId, (long) (page - 1) * pageSize, pageSize);
        return R.page(items, total, page, pageSize);
    }

    @Log("收藏岗位")
    @Operation(summary = "收藏岗位")
    @PostMapping("/{jobId}")
    public R<?> addFavorite(@PathVariable Long jobId, @RequestBody(required = false) Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();

        Long exists = favoriteMapper.selectCount(
                new LambdaQueryWrapper<JobFavorite>()
                        .eq(JobFavorite::getUserId, userId)
                        .eq(JobFavorite::getJobId, jobId));
        if (exists > 0) {
            return R.ok("已收藏");
        }

        JobFavorite fav = new JobFavorite();
        fav.setUserId(userId);
        fav.setJobId(jobId);
        fav.setNote(body != null ? body.get("note") : null);
        fav.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(fav);
        return R.ok("收藏成功");
    }

    @Log("取消收藏")
    @Operation(summary = "取消收藏")
    @DeleteMapping("/{jobId}")
    public R<?> removeFavorite(@PathVariable Long jobId) {
        Long userId = SecurityUtils.getCurrentUserId();
        favoriteMapper.delete(
                new LambdaQueryWrapper<JobFavorite>()
                        .eq(JobFavorite::getUserId, userId)
                        .eq(JobFavorite::getJobId, jobId));
        return R.ok("已取消收藏");
    }

    @Operation(summary = "检查是否已收藏")
    @GetMapping("/{jobId}/check")
    public R<?> checkFavorite(@PathVariable Long jobId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<JobFavorite>()
                        .eq(JobFavorite::getUserId, userId)
                        .eq(JobFavorite::getJobId, jobId));
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", count > 0);
        return R.ok(result);
    }
}
