package com.career.platform.recommend.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Tag(name = "Recommendation", description = "Job recommendation, skill gap, and similar jobs")
@RestController
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    private final JobPostingMapper jobMapper;
    private final WebClient algorithmWebClient;

    public RecommendController(JobPostingMapper jobMapper,
                               @Qualifier("algorithmWebClient") WebClient algorithmWebClient) {
        this.jobMapper = jobMapper;
        this.algorithmWebClient = algorithmWebClient;
    }

    @Data
    public static class JobRecommendRequest {
        private List<String> skills = Collections.emptyList();
        private List<String> preferredCities = Collections.emptyList();
        private String education;
        private String experience;
        private Double salaryMin;
        private Double salaryMax;
        private String industry;
        private Integer limit = 20;
    }

    @Data
    public static class SkillAdviceRequest {
        private List<String> userSkills = Collections.emptyList();
        private String targetJobType;
        private String city;
    }

    @Log("岗位推荐")
    @Operation(summary = "Recommend jobs")
    @PostMapping("/jobs")
    public R<?> recommendJobs(@RequestBody JobRecommendRequest req) {
        try {
            int safeLimit = req.getLimit() == null ? 20 : Math.min(Math.max(req.getLimit(), 1), 30);
            Map<String, Object> params = new HashMap<>();
            params.put("skills", req.getSkills());
            params.put("preferred_cities", req.getPreferredCities());
            params.put("education", req.getEducation());
            params.put("experience", req.getExperience());
            params.put("salary_min", req.getSalaryMin());
            params.put("salary_max", req.getSalaryMax());
            params.put("industry", req.getIndustry());
            params.put("limit", safeLimit);

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/match")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.fail("Recommendation service unavailable: " + e.getMessage());
        }
    }

    @Log("技能缺口分析")
    @Operation(summary = "Skill gap advice")
    @PostMapping("/skills")
    public R<?> skillAdvice(@RequestBody SkillAdviceRequest req) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("user_skills", req.getUserSkills());
            params.put("target_job_type", req.getTargetJobType());
            params.put("city", req.getCity());

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/skills/gap")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.fail("Algorithm service unavailable: " + e.getMessage());
        }
    }

    @Operation(summary = "Similar jobs")
    @GetMapping("/similar-jobs/{jobId}")
    public R<?> similarJobs(@PathVariable Long jobId, @RequestParam(defaultValue = "10") int limit) {
        JobPosting target = jobMapper.selectById(jobId);
        if (target == null) {
            throw BusinessException.notFound("Job not found");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 30);
        List<String> targetSkills = jobMapper.jobSkills(jobId);
        List<Map<String, Object>> similarJobs = jobMapper.similarJobsBySkills(jobId, target.getCity(), safeLimit);

        Map<String, Object> targetInfo = new HashMap<>();
        targetInfo.put("id", target.getId());
        targetInfo.put("title", target.getTitle());
        targetInfo.put("companyName", target.getCompanyName() == null ? "" : target.getCompanyName());
        targetInfo.put("city", target.getCity() == null ? "" : target.getCity());
        targetInfo.put("industryName", target.getIndustryName() == null ? "" : target.getIndustryName());
        targetInfo.put("skills", targetSkills);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetJob", targetInfo);
        payload.put("recommendations", similarJobs);
        payload.put("total", similarJobs.size());

        return R.ok(payload);
    }
}
