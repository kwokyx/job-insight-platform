package com.career.platform.open.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.result.R;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Open API", description = "Public read-only APIs")
@RestController
@RequestMapping("/api/v1/open")
@RequiredArgsConstructor
public class OpenApiController {

    private final JobPostingMapper jobMapper;

    @Operation(summary = "Public jobs query")
    @GetMapping("/jobs")
    public R<?> openJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);

        LambdaQueryWrapper<JobPosting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobPosting::getIsActive, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(JobPosting::getTitle, keyword).or().like(JobPosting::getCompanyName, keyword));
        }
        if (StringUtils.hasText(city)) {
            wrapper.like(JobPosting::getCity, city);
        }
        if (StringUtils.hasText(industry)) {
            wrapper.like(JobPosting::getIndustryName, industry);
        }
        wrapper.orderByDesc(JobPosting::getPublishDate);

        IPage<JobPosting> result = jobMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(job -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", job.getId());
            item.put("title", job.getTitle());
            item.put("companyName", job.getCompanyName());
            item.put("city", job.getCity());
            item.put("industryName", job.getIndustryName());
            item.put("education", job.getEducation());
            item.put("experience", job.getExperience());
            item.put("salaryText", job.getSalaryText());
            item.put("publishDate", job.getPublishDate());
            return item;
        }).collect(Collectors.toList());

        return R.page(records, result.getTotal(), safePage, safePageSize);
    }

    @Operation(summary = "Public overview analysis")
    @GetMapping("/analysis/overview")
    public R<?> openOverview() {
        Map<String, Object> stats = new HashMap<>(jobMapper.overviewStats());
        stats.put("totalJobs", jobMapper.selectCount(null));
        stats.put("topCities", jobMapper.aggregateByCity(10));
        stats.put("topIndustries", jobMapper.aggregateByIndustry(10));
        return R.ok(stats);
    }

    @Operation(summary = "Public skills ranking")
    @GetMapping("/analysis/skills")
    public R<?> openSkills(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return R.ok(jobMapper.topSkills(safeLimit));
    }
}
