package com.career.platform.recommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.profile.entity.Skill;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.entity.UserSkill;
import com.career.platform.profile.mapper.SkillMapper;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.career.platform.profile.mapper.UserSkillMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Recommendation", description = "Job recommendation, skill gap, and similar jobs")
@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final JobPostingMapper jobMapper;
    private final WebClient algorithmWebClient;
    private final UserProfileMapper userProfileMapper;
    private final UserSkillMapper userSkillMapper;
    private final SkillMapper skillMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserInsightService userInsightService;

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

    @Data
    public static class CareerPathRequest {
        private String currentJob;
        private String targetJob;
        private List<String> currentSkills = Collections.emptyList();
        private String city;
    }

    @Data
    public static class ResumeReviewRequest {
        private String targetJob;
        private String resumeText;
        private List<String> userSkills = Collections.emptyList();
    }

    @Log("岗位推荐")
    @Operation(summary = "Recommend jobs")
    @PostMapping("/jobs")
    public R<?> recommendJobs(@RequestBody JobRecommendRequest req) {
        JobRecommendRequest normalized = enrichRequestFromProfile(req);
        try {
            int safeLimit = normalized.getLimit() == null ? 20 : Math.min(Math.max(normalized.getLimit(), 1), 30);
            Map<String, Object> params = new HashMap<>();
            params.put("skills", normalized.getSkills());
            params.put("preferred_cities", normalized.getPreferredCities());
            params.put("education", normalized.getEducation());
            params.put("experience", normalized.getExperience());
            params.put("salary_min", normalized.getSalaryMin());
            params.put("salary_max", normalized.getSalaryMax());
            params.put("industry", normalized.getIndustry());
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
            try {
                return R.ok(buildJobFallback(normalized));
            } catch (Exception fallbackError) {
                return R.ok(minimalJobFallback(normalized, fallbackError));
            }
        }
    }

    @Log("技能缺口分析")
    @Operation(summary = "Skill gap advice")
    @PostMapping("/skills")
    public R<?> skillAdvice(@RequestBody SkillAdviceRequest req) {
        SkillAdviceRequest normalized = enrichSkillAdviceFromProfile(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("user_skills", normalized.getUserSkills());
            params.put("target_job_type", normalized.getTargetJobType());
            params.put("city", normalized.getCity());

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/skills/gap")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            try {
                return R.ok(buildSkillGapFallback(normalized));
            } catch (Exception fallbackError) {
                return R.ok(minimalSkillGapFallback(normalized, fallbackError));
            }
        }
    }

    @Log("职业路径模拟")
    @Operation(summary = "Career path simulation")
    @PostMapping("/career-path")
    public R<?> careerPath(@RequestBody CareerPathRequest req) {
        CareerPathRequest normalized = enrichCareerPathFromProfile(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("current_job", normalized.getCurrentJob());
            params.put("target_job", normalized.getTargetJob());
            params.put("current_skills", normalized.getCurrentSkills());
            params.put("city", normalized.getCity());

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/career/path")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            try {
                return R.ok(buildCareerPathFallback(normalized));
            } catch (Exception fallbackError) {
                return R.ok(minimalCareerPathFallback(normalized, fallbackError));
            }
        }
    }

    @Log("技能雷达诊断")
    @Operation(summary = "Skill radar")
    @PostMapping("/skill-radar")
    public R<?> skillRadar(@RequestBody SkillAdviceRequest req) {
        SkillAdviceRequest normalized = enrichSkillAdviceFromProfile(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("user_skills", normalized.getUserSkills());
            params.put("target_job_type", normalized.getTargetJobType());
            params.put("city", normalized.getCity());

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/skills/radar")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            try {
                return R.ok(buildSkillRadarFallback(normalized));
            } catch (Exception fallbackError) {
                return R.ok(minimalSkillRadarFallback(normalized, fallbackError));
            }
        }
    }

    @Log("简历优化建议")
    @Operation(summary = "Resume review advice")
    @PostMapping("/resume-review")
    public R<?> resumeReview(@RequestBody ResumeReviewRequest req) {
        ResumeReviewRequest normalized = enrichResumeReviewFromProfile(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("target_job", normalized.getTargetJob());
            params.put("resume_text", normalized.getResumeText());
            params.put("user_skills", normalized.getUserSkills());

            Object result = algorithmWebClient.post()
                    .uri("/algorithm/resume/review")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            try {
                return R.ok(buildResumeReviewFallback(normalized));
            } catch (Exception fallbackError) {
                return R.ok(minimalResumeReviewFallback(normalized, fallbackError));
            }
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
        targetInfo.put("companyName", defaultString(target.getCompanyName()));
        targetInfo.put("city", defaultString(target.getCity()));
        targetInfo.put("industryName", defaultString(target.getIndustryName()));
        targetInfo.put("skills", targetSkills);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetJob", targetInfo);
        payload.put("recommendations", similarJobs);
        payload.put("total", similarJobs.size());
        return R.ok(payload);
    }

    @Operation(summary = "Personalized recommendation plan")
    @GetMapping("/plan")
    public R<?> personalizedPlan() {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("Please login first");
        }

        Map<String, Object> advisory = userInsightService.buildPlatformAdvisory(userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> userContext = (Map<String, Object>) advisory.getOrDefault("userContext", Collections.emptyMap());

        JobRecommendRequest req = new JobRecommendRequest();
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) userContext.getOrDefault("skills", Collections.emptyList());
        req.setSkills(skills);
        req.setPreferredCities(singletonIfText(String.valueOf(userContext.getOrDefault("targetCityCode", ""))));
        req.setEducation(String.valueOf(userContext.getOrDefault("educationLevel", "")));
        req.setLimit(8);

        Map<String, Object> jobs = buildJobFallback(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advisory", advisory);
        result.put("recommendedJobs", jobs.getOrDefault("items", Collections.emptyList()));
        result.put("totalJobs", jobs.getOrDefault("total", 0));
        return R.ok(result);
    }

    private JobRecommendRequest enrichRequestFromProfile(JobRecommendRequest req) {
        JobRecommendRequest copy = req == null ? new JobRecommendRequest() : req;
        Long userId = currentUserId();
        if (userId == null || userProfileMapper == null) {
            return copy;
        }

        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("LIMIT 1")
        );
        if (profile == null) {
            return copy;
        }

        if ((copy.getSkills() == null || copy.getSkills().isEmpty()) && userSkillMapper != null && skillMapper != null) {
            copy.setSkills(loadUserSkillNames(profile.getId(), profile.getSkills()));
        }
        if ((copy.getPreferredCities() == null || copy.getPreferredCities().isEmpty()) && StringUtils.hasText(profile.getTargetCityCode())) {
            copy.setPreferredCities(Collections.singletonList(profile.getTargetCityCode()));
        }
        if (!StringUtils.hasText(copy.getEducation())) {
            copy.setEducation(profile.getEducationLevel());
        }
        return copy;
    }

    private SkillAdviceRequest enrichSkillAdviceFromProfile(SkillAdviceRequest req) {
        SkillAdviceRequest copy = req == null ? new SkillAdviceRequest() : req;
        Long userId = currentUserId();
        if (userId == null || userProfileMapper == null) {
            return copy;
        }
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("LIMIT 1")
        );
        if (profile == null) {
            return copy;
        }
        if ((copy.getUserSkills() == null || copy.getUserSkills().isEmpty()) && userSkillMapper != null && skillMapper != null) {
            copy.setUserSkills(loadUserSkillNames(profile.getId(), profile.getSkills()));
        }
        if (!StringUtils.hasText(copy.getTargetJobType())) {
            copy.setTargetJobType(profile.getProfileSummary());
        }
        if (!StringUtils.hasText(copy.getCity()) && StringUtils.hasText(profile.getTargetCityCode())) {
            copy.setCity(profile.getTargetCityCode());
        }
        return copy;
    }

    private CareerPathRequest enrichCareerPathFromProfile(CareerPathRequest req) {
        CareerPathRequest copy = req == null ? new CareerPathRequest() : req;
        Long userId = currentUserId();
        if (userId == null || userProfileMapper == null) {
            return copy;
        }
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("LIMIT 1")
        );
        if (profile == null) {
            return copy;
        }
        if ((copy.getCurrentSkills() == null || copy.getCurrentSkills().isEmpty()) && userSkillMapper != null && skillMapper != null) {
            copy.setCurrentSkills(loadUserSkillNames(profile.getId(), profile.getSkills()));
        }
        if (!StringUtils.hasText(copy.getTargetJob())) {
            copy.setTargetJob(profile.getProfileSummary());
        }
        if (!StringUtils.hasText(copy.getCity()) && StringUtils.hasText(profile.getTargetCityCode())) {
            copy.setCity(profile.getTargetCityCode());
        }
        return copy;
    }

    private ResumeReviewRequest enrichResumeReviewFromProfile(ResumeReviewRequest req) {
        ResumeReviewRequest copy = req == null ? new ResumeReviewRequest() : req;
        Long userId = currentUserId();
        if (userId == null || userProfileMapper == null) {
            return copy;
        }
        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("LIMIT 1")
        );
        if (profile == null) {
            return copy;
        }
        if ((copy.getUserSkills() == null || copy.getUserSkills().isEmpty()) && userSkillMapper != null && skillMapper != null) {
            copy.setUserSkills(loadUserSkillNames(profile.getId(), profile.getSkills()));
        }
        if (!StringUtils.hasText(copy.getTargetJob())) {
            copy.setTargetJob(profile.getProfileSummary());
        }
        return copy;
    }

    private Map<String, Object> buildJobFallback(JobRecommendRequest req) {
        int safeLimit = req.getLimit() == null ? 20 : Math.min(Math.max(req.getLimit(), 1), 30);
        List<String> expectedSkills = normalizeStrings(req.getSkills());
        List<String> preferredCities = normalizeStrings(req.getPreferredCities());

        LambdaQueryWrapper<JobPosting> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(JobPosting::getPublishDate)
                .last("LIMIT 200");
        if (StringUtils.hasText(req.getIndustry())) {
            wrapper.like(JobPosting::getIndustryName, req.getIndustry().trim());
        }
        if (StringUtils.hasText(req.getEducation())) {
            wrapper.like(JobPosting::getEducation, req.getEducation().trim());
        }
        if (StringUtils.hasText(req.getExperience())) {
            wrapper.like(JobPosting::getExperience, req.getExperience().trim());
        }

        List<JobPosting> candidates = jobMapper.selectList(wrapper);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (JobPosting job : candidates) {
            double score = 0;
            List<String> jobSkills = normalizeStrings(jobMapper.jobSkills(job.getId()));
            Set<String> matchedSkills = new LinkedHashSet<>(jobSkills);
            matchedSkills.retainAll(expectedSkills);
            score += matchedSkills.size() * 20;
            if (!preferredCities.isEmpty() && containsLike(preferredCities, job.getCity())) {
                score += 15;
            }
            if (StringUtils.hasText(req.getIndustry()) && StringUtils.hasText(job.getIndustryName())
                    && job.getIndustryName().toLowerCase().contains(req.getIndustry().toLowerCase())) {
                score += 10;
            }
            if (req.getSalaryMin() != null && job.getSalaryMax() != null
                    && job.getSalaryMax().doubleValue() >= req.getSalaryMin()) {
                score += 10;
            }
            if (req.getSalaryMax() != null && job.getSalaryMin() != null
                    && job.getSalaryMin().doubleValue() <= req.getSalaryMax()) {
                score += 5;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobId", job.getId());
            item.put("title", job.getTitle());
            item.put("companyName", job.getCompanyName());
            item.put("city", job.getCity());
            item.put("industryName", job.getIndustryName());
            item.put("salaryText", job.getSalaryText());
            item.put("publishDate", job.getPublishDate());
            item.put("matchedSkills", new ArrayList<>(matchedSkills));
            item.put("whyMatched", buildWhyMatched(matchedSkills, req, job));
            item.put("missingSkills", topMissingSkills(expectedSkills, jobSkills, 5));
            item.put("nextActions", buildJobNextActions(expectedSkills, jobSkills, job));
            item.put("confidence", Math.min(100, (int) Math.round(score)));
            item.put("score", score);
            scored.add(item);
        }

        scored.sort(Comparator.comparingDouble(item -> -((Number) item.get("score")).doubleValue()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "local_fallback");
        payload.put("total", Math.min(safeLimit, scored.size()));
        payload.put("items", scored.subList(0, Math.min(safeLimit, scored.size())));
        payload.put("explainability", "Each job includes whyMatched, missingSkills, nextActions, and confidence.");
        return payload;
    }

    private Map<String, Object> buildSkillGapFallback(SkillAdviceRequest req) {
        List<String> userSkills = normalizeStrings(req.getUserSkills());
        List<Map<String, Object>> marketSkills = queryMarketSkills(req.getTargetJobType(), req.getCity(), 20);
        Set<String> userSkillSet = new HashSet<>(userSkills);
        List<Map<String, Object>> missing = new ArrayList<>();
        List<String> matched = new ArrayList<>();

        for (Map<String, Object> row : marketSkills) {
            String skill = String.valueOf(row.get("skill"));
            if (userSkillSet.contains(skill.toLowerCase())) {
                matched.add(skill);
            } else {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skill", skill);
                item.put("marketDemand", row.get("count"));
                missing.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "local_fallback");
        payload.put("targetJobType", req.getTargetJobType());
        payload.put("matchedSkills", matched);
        payload.put("missingSkills", missing);
        payload.put("matchRate", marketSkills.isEmpty() ? "0.0%" : String.format("%.1f%%", matched.size() * 100.0 / marketSkills.size()));
        payload.put("nextActions", Arrays.asList(
                "Prioritize top-3 missing skills in current month",
                "Create one portfolio project for target role",
                "Re-run recommendation after skill update"
        ));
        return payload;
    }

    private Map<String, Object> buildCareerPathFallback(CareerPathRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "local_fallback");
        payload.put("currentJob", req.getCurrentJob());
        payload.put("targetJob", req.getTargetJob());

        List<Map<String, Object>> paths = queryCareerPaths(req.getCurrentJob(), req.getTargetJob());
        if (!paths.isEmpty()) {
            payload.put("path", paths);
        } else {
            payload.put("path", Arrays.asList(
                    defaultString(req.getCurrentJob()),
                    "中级岗位",
                    defaultString(req.getTargetJob())
            ));
        }

        Map<String, Object> gap = buildSkillGapFallback(toSkillAdvice(req.getCurrentSkills(), req.getTargetJob(), req.getCity()));
        payload.put("missingSkills", gap.get("missingSkills"));
        return payload;
    }

    private Map<String, Object> buildSkillRadarFallback(SkillAdviceRequest req) {
        List<String> userSkills = normalizeStrings(req.getUserSkills());
        List<Map<String, Object>> marketSkills = queryMarketSkills(req.getTargetJobType(), req.getCity(), 8);
        Set<String> userSkillSet = new HashSet<>(userSkills);

        List<Map<String, Object>> radar = new ArrayList<>();
        for (Map<String, Object> row : marketSkills) {
            String skill = String.valueOf(row.get("skill"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skill", skill);
            item.put("score", userSkillSet.contains(skill.toLowerCase()) ? 85 : 35);
            item.put("marketDemand", row.get("count"));
            radar.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "local_fallback");
        payload.put("targetJobType", req.getTargetJobType());
        payload.put("radar", radar);
        return payload;
    }

    private Map<String, Object> buildResumeReviewFallback(ResumeReviewRequest req) {
        List<Map<String, Object>> marketSkills = queryMarketSkills(req.getTargetJob(), null, 10);
        Set<String> userSkillSet = new HashSet<>(normalizeStrings(req.getUserSkills()));
        List<String> missingKeywords = new ArrayList<>();
        for (Map<String, Object> row : marketSkills) {
            String skill = String.valueOf(row.get("skill"));
            if (!userSkillSet.contains(skill.toLowerCase())) {
                missingKeywords.add(skill);
            }
        }

        List<String> suggestions = new ArrayList<>();
        suggestions.add("补充项目成果量化指标");
        suggestions.add("突出与你目标岗位匹配的技能关键词");
        if (!missingKeywords.isEmpty()) {
            suggestions.add("优先补充这些高频技能关键词: " + String.join("、", missingKeywords.subList(0, Math.min(3, missingKeywords.size()))));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "local_fallback");
        payload.put("targetJob", req.getTargetJob());
        payload.put("strengths", req.getUserSkills());
        payload.put("suggestions", suggestions);
        payload.put("missingKeywords", missingKeywords);
        return payload;
    }

    private List<Map<String, Object>> queryMarketSkills(String targetJobType, String city, int limit) {
        if (jdbcTemplate == null) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT d.label_name AS skill, COUNT(*) AS count " +
                        "FROM job_label_rel r " +
                        "JOIN job_label_dict d ON r.label_id = d.id " +
                        "JOIN biz_job_posting jp ON r.job_posting_id = jp.id " +
                        "WHERE 1 = 1 "
        );
        List<Object> args = new ArrayList<>();
        sql.append("AND d.label_type IN ('skill', 'tool', 'language', 'framework') ");
        if (StringUtils.hasText(targetJobType)) {
            sql.append("AND jp.title LIKE ? ");
            args.add("%" + targetJobType.trim() + "%");
        }
        if (StringUtils.hasText(city)) {
            sql.append("AND jp.job_city LIKE ? ");
            args.add("%" + city.trim() + "%");
        }
        sql.append("GROUP BY d.id, d.label_name ORDER BY count DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> queryCareerPaths(String currentJob, String targetJob) {
        if (jdbcTemplate == null || (!StringUtils.hasText(currentJob) && !StringUtils.hasText(targetJob))) {
            return Collections.emptyList();
        }
        String keyword = StringUtils.hasText(targetJob) ? targetJob : currentJob;
        try {
            return jdbcTemplate.queryForList(
                    "SELECT job_title_from AS fromJob, job_title_to AS toJob, transition_type AS transitionType, " +
                            "avg_years AS avgYears, required_skills AS requiredSkills, frequency " +
                            "FROM biz_career_path WHERE job_title_from LIKE ? OR job_title_to LIKE ? " +
                            "ORDER BY frequency DESC LIMIT 10",
                    "%" + keyword + "%", "%" + keyword + "%"
            );
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> loadUserSkillNames(Long profileId, String profileSkillsJson) {
        List<UserSkill> userSkills = userSkillMapper.selectList(
                new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getProfileId, profileId)
        );
        if (userSkills.isEmpty()) {
            return parseStringList(profileSkillsJson);
        }
        List<Long> skillIds = userSkills.stream().map(UserSkill::getSkillId).collect(Collectors.toList());
        Map<Long, String> skillMap = skillMapper.selectBatchIds(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getSkillName, (a, b) -> a));
        List<String> result = new ArrayList<>();
        for (UserSkill userSkill : userSkills) {
            String name = skillMap.get(userSkill.getSkillId());
            if (StringUtils.hasText(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> singletonIfText(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(value.trim());
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase())
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean containsLike(List<String> expected, String actual) {
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        String normalized = actual.trim().toLowerCase();
        for (String item : expected) {
            if (normalized.contains(item) || item.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private SkillAdviceRequest toSkillAdvice(List<String> skills, String targetJobType, String city) {
        SkillAdviceRequest req = new SkillAdviceRequest();
        req.setUserSkills(skills == null ? Collections.emptyList() : skills);
        req.setTargetJobType(targetJobType);
        req.setCity(city);
        return req;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        if (auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private List<String> buildWhyMatched(Set<String> matchedSkills, JobRecommendRequest req, JobPosting job) {
        List<String> reasons = new ArrayList<>();
        if (!matchedSkills.isEmpty()) {
            reasons.add("Matched skills: " + String.join(", ", matchedSkills));
        }
        if (StringUtils.hasText(job.getCity()) && containsLike(normalizeStrings(req.getPreferredCities()), job.getCity())) {
            reasons.add("Location preference matched: " + job.getCity());
        }
        if (StringUtils.hasText(req.getIndustry()) && StringUtils.hasText(job.getIndustryName())
                && job.getIndustryName().toLowerCase().contains(req.getIndustry().toLowerCase())) {
            reasons.add("Industry preference matched: " + req.getIndustry());
        }
        if (reasons.isEmpty()) {
            reasons.add("Matched based on recency and baseline market fit.");
        }
        return reasons;
    }

    private List<String> topMissingSkills(List<String> expectedSkills, List<String> jobSkills, int limit) {
        Set<String> existing = new LinkedHashSet<>(jobSkills);
        List<String> missing = new ArrayList<>();
        for (String skill : expectedSkills) {
            if (!existing.contains(skill) && missing.size() < limit) {
                missing.add(skill);
            }
        }
        return missing;
    }

    private List<String> buildJobNextActions(List<String> expectedSkills, List<String> jobSkills, JobPosting job) {
        List<String> actions = new ArrayList<>();
        List<String> missing = topMissingSkills(expectedSkills, jobSkills, 3);
        if (!missing.isEmpty()) {
            actions.add("Learn missing skills: " + String.join(", ", missing));
        }
        actions.add("Tailor resume for " + defaultString(job.getTitle()) + " with quantified project impact.");
        actions.add("Prepare interview stories aligned to " + defaultString(job.getIndustryName()) + " scenarios.");
        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Object> raw = (List<Object>) value;
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String firstOf(Object value) {
        List<String> list = asStringList(value);
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<String, Object> minimalJobFallback(JobRecommendRequest req, Exception error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "minimal_fallback");
        payload.put("total", 0);
        payload.put("items", Collections.emptyList());
        payload.put("message", "Job recommendation fallback used");
        payload.put("error", error.getMessage());
        payload.put("requestedSkills", req.getSkills());
        payload.put("preferredCities", req.getPreferredCities());
        return payload;
    }

    private Map<String, Object> minimalSkillGapFallback(SkillAdviceRequest req, Exception error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "minimal_fallback");
        payload.put("targetJobType", req.getTargetJobType());
        payload.put("matchedSkills", Collections.emptyList());
        payload.put("missingSkills", Collections.emptyList());
        payload.put("matchRate", "0.0%");
        payload.put("error", error.getMessage());
        return payload;
    }

    private Map<String, Object> minimalCareerPathFallback(CareerPathRequest req, Exception error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "minimal_fallback");
        payload.put("currentJob", req.getCurrentJob());
        payload.put("targetJob", req.getTargetJob());
        payload.put("path", Arrays.asList(defaultString(req.getCurrentJob()), "target preparation", defaultString(req.getTargetJob())));
        payload.put("missingSkills", Collections.emptyList());
        payload.put("error", error.getMessage());
        return payload;
    }

    private Map<String, Object> minimalSkillRadarFallback(SkillAdviceRequest req, Exception error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "minimal_fallback");
        payload.put("targetJobType", req.getTargetJobType());
        payload.put("radar", Collections.emptyList());
        payload.put("error", error.getMessage());
        return payload;
    }

    private Map<String, Object> minimalResumeReviewFallback(ResumeReviewRequest req, Exception error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "minimal_fallback");
        payload.put("targetJob", req.getTargetJob());
        payload.put("strengths", req.getUserSkills() == null ? Collections.emptyList() : req.getUserSkills());
        payload.put("suggestions", Arrays.asList(
                "Add measurable outcomes.",
                "Align the resume wording with the target job.",
                "Expand project and skill evidence."
        ));
        payload.put("missingKeywords", Collections.emptyList());
        payload.put("error", error.getMessage());
        return payload;
    }
}
