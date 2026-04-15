package com.career.platform.recommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.profile.entity.UserProfile;
import com.career.platform.profile.mapper.UserProfileMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Recommendation", description = "Job recommendation, skill gap, and similar jobs")
@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private static final List<String> DEFAULT_SKILLS = Arrays.asList(
            "Java", "Spring Boot", "MySQL", "Redis", "Vue", "Python", "Docker", "Git"
    );

    private final JobPostingMapper jobMapper;
    private final WebClient algorithmWebClient;
    private final UserProfileMapper userProfileMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MarketSkillService marketSkillService;
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

    @Log("Recommend jobs")
    @Operation(summary = "Recommend jobs")
    @PostMapping("/jobs")
    public R<?> recommendJobs(@RequestBody JobRecommendRequest req) {
        JobRecommendRequest normalized = enrichJobRequest(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("skills", normalized.getSkills());
            params.put("preferred_cities", normalized.getPreferredCities());
            params.put("education", normalized.getEducation());
            params.put("experience", normalized.getExperience());
            params.put("salary_min", normalized.getSalaryMin());
            params.put("salary_max", normalized.getSalaryMax());
            params.put("industry", normalized.getIndustry());
            params.put("limit", safeLimit(normalized.getLimit(), 20, 30));
            Object result = algorithmWebClient.post()
                    .uri("/algorithm/match")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.ok(buildJobFallback(normalized));
        }
    }

    @Log("Skill gap analysis")
    @Operation(summary = "Skill gap advice")
    @PostMapping("/skills")
    public R<?> skillAdvice(@RequestBody SkillAdviceRequest req) {
        SkillAdviceRequest normalized = enrichSkillRequest(req);
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
                    .timeout(Duration.ofSeconds(20))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.ok(buildSkillGapFallback(normalized));
        }
    }

    @Log("Career path simulation")
    @Operation(summary = "Career path simulation")
    @PostMapping("/career-path")
    public R<?> careerPath(@RequestBody CareerPathRequest req) {
        CareerPathRequest normalized = enrichCareerPathRequest(req);
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
                    .timeout(Duration.ofSeconds(20))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.ok(buildCareerPathFallback(normalized));
        }
    }

    @Log("Skill radar")
    @Operation(summary = "Skill radar")
    @PostMapping("/skill-radar")
    public R<?> skillRadar(@RequestBody SkillAdviceRequest req) {
        SkillAdviceRequest normalized = enrichSkillRequest(req);
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
                    .timeout(Duration.ofSeconds(20))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.ok(buildSkillRadarFallback(normalized));
        }
    }

    @Log("Resume review")
    @Operation(summary = "Resume review advice")
    @PostMapping("/resume-review")
    public R<?> resumeReview(@RequestBody ResumeReviewRequest req) {
        ResumeReviewRequest normalized = enrichResumeRequest(req);
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
                    .timeout(Duration.ofSeconds(20))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            return R.ok(buildResumeReviewFallback(normalized));
        }
    }

    @Operation(summary = "Similar jobs")
    @GetMapping("/similar-jobs/{jobId}")
    public R<?> similarJobs(@PathVariable Long jobId, @RequestParam(defaultValue = "10") int limit) {
        JobPosting target = jobMapper.selectById(jobId);
        if (target == null) {
            throw BusinessException.notFound("Job not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJob", target);
        result.put("recommendations", jobMapper.similarJobsBySkills(jobId, target.getCity(), safeLimit(limit, 10, 30)));
        return R.ok(result);
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
        SkillAdviceRequest skillReq = new SkillAdviceRequest();
        skillReq.setUserSkills(asStringList(userContext.get("skills")));
        skillReq.setTargetJobType(stringValue(userContext.get("profileSummary")));
        skillReq.setCity(stringValue(userContext.get("targetCityCode")));
        JobRecommendRequest jobReq = new JobRecommendRequest();
        jobReq.setSkills(skillReq.getUserSkills());
        jobReq.setPreferredCities(singletonIfText(skillReq.getCity()));
        jobReq.setLimit(8);

        Map<String, Object> jobs = buildJobFallback(jobReq);
        Map<String, Object> gaps = buildSkillGapFallback(skillReq);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advisory", advisory);
        result.put("recommendedJobs", jobs.getOrDefault("items", Collections.emptyList()));
        result.put("skillGap", gaps);
        result.put("planSummary", buildPlanSummary(jobs, gaps));
        return R.ok(result);
    }

    private JobRecommendRequest enrichJobRequest(JobRecommendRequest req) {
        JobRecommendRequest normalized = req == null ? new JobRecommendRequest() : req;
        UserProfile profile = loadCurrentProfile();
        if (profile != null) {
            if (normalized.getSkills() == null || normalized.getSkills().isEmpty()) {
                normalized.setSkills(loadUserSkillNames(profile));
            } else {
                normalized.setSkills(normalizeStrings(normalized.getSkills()));
            }
            if ((normalized.getPreferredCities() == null || normalized.getPreferredCities().isEmpty())
                    && StringUtils.hasText(profile.getTargetCityCode())) {
                normalized.setPreferredCities(singletonIfText(profile.getTargetCityCode()));
            } else {
                normalized.setPreferredCities(normalizeStrings(normalized.getPreferredCities()));
            }
            if (!StringUtils.hasText(normalized.getEducation())) {
                normalized.setEducation(profile.getEducationLevel());
            }
            if (normalized.getSalaryMin() == null && profile.getExpectedSalaryMin() != null) {
                normalized.setSalaryMin(profile.getExpectedSalaryMin().doubleValue());
            }
            if (normalized.getSalaryMax() == null && profile.getExpectedSalaryMax() != null) {
                normalized.setSalaryMax(profile.getExpectedSalaryMax().doubleValue());
            }
            if (!StringUtils.hasText(normalized.getIndustry())) {
                normalized.setIndustry(profile.getProfileSummary());
            }
        } else {
            normalized.setSkills(normalizeStrings(normalized.getSkills()));
            normalized.setPreferredCities(normalizeStrings(normalized.getPreferredCities()));
        }
        normalized.setLimit(safeLimit(normalized.getLimit(), 20, 30));
        return normalized;
    }

    private SkillAdviceRequest enrichSkillRequest(SkillAdviceRequest req) {
        SkillAdviceRequest normalized = req == null ? new SkillAdviceRequest() : req;
        UserProfile profile = loadCurrentProfile();
        if (profile != null) {
            if (normalized.getUserSkills() == null || normalized.getUserSkills().isEmpty()) {
                normalized.setUserSkills(loadUserSkillNames(profile));
            } else {
                normalized.setUserSkills(normalizeStrings(normalized.getUserSkills()));
            }
            if (!StringUtils.hasText(normalized.getTargetJobType())) {
                normalized.setTargetJobType(firstNonBlank(profile.getProfileSummary(), inferTargetDirection(normalized.getUserSkills())));
            }
            if (!StringUtils.hasText(normalized.getCity())) {
                normalized.setCity(profile.getTargetCityCode());
            }
        } else {
            normalized.setUserSkills(normalizeStrings(normalized.getUserSkills()));
        }
        if (!StringUtils.hasText(normalized.getTargetJobType())) {
            normalized.setTargetJobType(inferTargetDirection(normalized.getUserSkills()));
        }
        return normalized;
    }

    private CareerPathRequest enrichCareerPathRequest(CareerPathRequest req) {
        CareerPathRequest normalized = req == null ? new CareerPathRequest() : req;
        UserProfile profile = loadCurrentProfile();
        if (profile != null) {
            if (normalized.getCurrentSkills() == null || normalized.getCurrentSkills().isEmpty()) {
                normalized.setCurrentSkills(loadUserSkillNames(profile));
            } else {
                normalized.setCurrentSkills(normalizeStrings(normalized.getCurrentSkills()));
            }
            if (!StringUtils.hasText(normalized.getCurrentJob())) {
                normalized.setCurrentJob(inferCurrentRole(profile, normalized.getCurrentSkills()));
            }
            if (!StringUtils.hasText(normalized.getTargetJob())) {
                normalized.setTargetJob(inferTargetDirection(normalized.getCurrentSkills()));
            }
            if (!StringUtils.hasText(normalized.getCity())) {
                normalized.setCity(profile.getTargetCityCode());
            }
        } else {
            normalized.setCurrentSkills(normalizeStrings(normalized.getCurrentSkills()));
        }
        if (!StringUtils.hasText(normalized.getCurrentJob())) {
            normalized.setCurrentJob(inferCurrentRole(null, normalized.getCurrentSkills()));
        }
        if (!StringUtils.hasText(normalized.getTargetJob())) {
            normalized.setTargetJob(inferTargetDirection(normalized.getCurrentSkills()));
        }
        return normalized;
    }

    private ResumeReviewRequest enrichResumeRequest(ResumeReviewRequest req) {
        ResumeReviewRequest normalized = req == null ? new ResumeReviewRequest() : req;
        UserProfile profile = loadCurrentProfile();
        if (profile != null) {
            if (normalized.getUserSkills() == null || normalized.getUserSkills().isEmpty()) {
                normalized.setUserSkills(loadUserSkillNames(profile));
            } else {
                normalized.setUserSkills(normalizeStrings(normalized.getUserSkills()));
            }
            if (!StringUtils.hasText(normalized.getTargetJob())) {
                normalized.setTargetJob(firstNonBlank(profile.getProfileSummary(), inferTargetDirection(normalized.getUserSkills())));
            }
        } else {
            normalized.setUserSkills(normalizeStrings(normalized.getUserSkills()));
        }
        if (!StringUtils.hasText(normalized.getResumeText())) {
            normalized.setResumeText("");
        }
        if (!StringUtils.hasText(normalized.getTargetJob())) {
            normalized.setTargetJob(inferTargetDirection(normalized.getUserSkills()));
        }
        return normalized;
    }

    private UserProfile loadCurrentProfile() {
        Long userId = currentUserId();
        if (userId == null) {
            return null;
        }
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .last("limit 1"));
    }

    private Map<String, Object> buildJobFallback(JobRecommendRequest req) {
        List<String> desiredSkills = normalizeStrings(req.getSkills());
        List<String> desiredCities = normalizeStrings(req.getPreferredCities());
        List<String> keywords = inferTargetKeywords(desiredSkills, req.getIndustry());
        Map<Long, JobPosting> candidateMap = new LinkedHashMap<>();

        List<JobPosting> recent = jobMapper.selectList(new LambdaQueryWrapper<JobPosting>()
                .orderByDesc(JobPosting::getPublishDate)
                .last("limit 160"));
        for (JobPosting job : recent) {
            candidateMap.put(job.getId(), job);
        }
        for (String keyword : keywords.stream().limit(4).collect(Collectors.toList())) {
            try {
                List<Map<String, Object>> rows = jobMapper.searchJobs(keyword, keyword, 0, 25);
                for (Map<String, Object> row : rows) {
                    Object idValue = row.get("id");
                    if (idValue instanceof Number) {
                        Long id = ((Number) idValue).longValue();
                        candidateMap.computeIfAbsent(id, jobMapper::selectById);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        List<JobPosting> candidates = candidateMap.values().stream()
                .filter(job -> job != null)
                .collect(Collectors.toList());

        List<Map<String, Object>> scored = new ArrayList<>();
        for (JobPosting job : candidates) {
            List<String> jobSkills = queryMarketSkills(job.getId(), 10);
            int skillMatches = countKeywordMatches(jobSkills, desiredSkills);
            int titleMatches = countKeywordMatches(
                    Arrays.asList(firstNonBlank(job.getTitle(), ""), firstNonBlank(job.getIndustryName(), ""), firstNonBlank(job.getDescription(), "")),
                    keywords
            );
            double score = 0.0;
            score += desiredSkills.isEmpty() ? 10 : Math.min(35, skillMatches * 7.0);
            score += Math.min(20, titleMatches * 6.0);
            if (containsLike(desiredCities, job.getCity())) {
                score += 15;
            }
            if (StringUtils.hasText(req.getIndustry())
                    && containsLike(singletonIfText(req.getIndustry()), firstNonBlank(job.getIndustryName(), job.getTitle()))) {
                score += 10;
            }
            score += countKeywordMatches(inferTargetKeywords(jobSkills, job.getTitle()), keywords) * 4.0;
            if (matchesEducation(req.getEducation(), job.getEducation())) {
                score += 8;
            }
            if (matchesExperience(req.getExperience(), job.getExperience())) {
                score += 8;
            }
            if (req.getSalaryMin() != null && job.getSalaryMax() != null
                    && job.getSalaryMax().doubleValue() >= req.getSalaryMin()) {
                score += 8;
            }
            if (req.getSalaryMax() != null && job.getSalaryMin() != null
                    && job.getSalaryMin().doubleValue() <= req.getSalaryMax()) {
                score += 4;
            }
            score += recencyScore(job.getPublishDate());

            if (!desiredSkills.isEmpty() && skillMatches == 0 && titleMatches == 0) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", job.getId());
            item.put("title", job.getTitle());
            item.put("companyName", job.getCompanyName());
            item.put("city", job.getCity());
            item.put("industryName", job.getIndustryName());
            item.put("education", job.getEducation());
            item.put("experience", job.getExperience());
            item.put("salaryMin", job.getSalaryMin());
            item.put("salaryMax", job.getSalaryMax());
            item.put("salaryText", job.getSalaryText());
            item.put("publishDate", job.getPublishDate());
            item.put("matchedSkills", desiredSkills.stream()
                    .filter(skill -> containsLike(jobSkills, skill))
                    .collect(Collectors.toList()));
            item.put("jobSkills", jobSkills);
            item.put("score", new BigDecimal(score).setScale(1, RoundingMode.HALF_UP));
            item.put("fitLabel", fitLabel(score));
            item.put("whyMatched", buildWhyMatched(req, job, jobSkills, skillMatches));
            item.put("nextActions", buildJobNextActions(req, job, jobSkills));
            scored.add(item);
        }

        scored.sort(Comparator.comparingDouble(item -> -((Number) item.get("score")).doubleValue()));
        List<Map<String, Object>> items = scored.stream()
                .limit(req.getLimit())
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", buildRecommendProfileSnapshot(req));
        result.put("summary", buildJobRecommendationSummary(req, items));
        result.put("items", items);
        result.put("marketHotSkills", queryMarketSkills(null, 8));
        return result;
    }

    private Map<String, Object> buildSkillGapFallback(SkillAdviceRequest req) {
        List<String> currentSkills = normalizeStrings(req.getUserSkills());
        List<String> marketSkills = queryMarketSkills(null, 16);
        List<String> targetSkills = queryCareerPaths(req.getTargetJobType()).stream()
                .flatMap(path -> parseStringList(path.get("requiredSkills")).stream())
                .collect(Collectors.toList());
        if (targetSkills.isEmpty()) {
            targetSkills = inferTargetKeywords(currentSkills, req.getTargetJobType());
            targetSkills.addAll(marketSkills);
        }
        targetSkills = normalizeStrings(targetSkills);
        List<String> normalizedTargetSkills = targetSkills;
        List<String> missingSkills = topMissingSkills(currentSkills, normalizedTargetSkills, 8);
        List<String> matchedSkills = currentSkills.stream()
                .filter(skill -> containsLike(normalizedTargetSkills, skill))
                .collect(Collectors.toList());

        double ratio = normalizedTargetSkills.isEmpty() ? 0.0 : (matchedSkills.size() * 100.0 / Math.max(1, normalizedTargetSkills.size()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJobType", firstNonBlank(req.getTargetJobType(), inferTargetDirection(currentSkills)));
        result.put("city", req.getCity());
        result.put("matchedSkills", matchedSkills);
        result.put("missingSkills", missingSkills);
        result.put("prioritySkills", missingSkills.stream().limit(5).collect(Collectors.toList()));
        result.put("entryStrengths", suggestEntryStrengths(currentSkills));
        result.put("learningPath", buildLearningPath(missingSkills));
        result.put("marketSignals", marketSkills.stream().limit(6).collect(Collectors.toList()));
        result.put("matchRate", String.format(Locale.US, "%.1f%%", ratio));
        result.put("summary", matchedSkills.isEmpty()
                ? "Current skills are still far from the target role. Build the priority stack first."
                : "You already cover the core foundation. Focus on the listed missing skills to close the gap.");
        return result;
    }

    private Map<String, Object> buildCareerPathFallback(CareerPathRequest req) {
        List<Map<String, Object>> knownPaths = queryCareerPaths(req.getTargetJob());
        List<Map<String, Object>> steps = knownPaths.isEmpty()
                ? buildSyntheticCareerPath(req)
                : knownPaths;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentJob", req.getCurrentJob());
        result.put("targetJob", req.getTargetJob());
        result.put("city", req.getCity());
        result.put("direction", inferTargetDirection(req.getCurrentSkills()));
        result.put("steps", steps);
        result.put("milestones", buildCareerMilestones(req, steps));
        result.put("recommendedProjects", buildRecommendedProjects(req));
        result.put("timelineSummary", String.format(
                Locale.US,
                "Expected transition path: %d stages over roughly %d-%d months.",
                steps.size(),
                Math.max(6, steps.size() * 3),
                Math.max(9, steps.size() * 5)
        ));
        return result;
    }

    private Map<String, Object> buildSkillRadarFallback(SkillAdviceRequest req) {
        List<String> currentSkills = normalizeStrings(req.getUserSkills());
        List<String> targetSkills = inferTargetKeywords(currentSkills, req.getTargetJobType());
        targetSkills.addAll(queryMarketSkills(null, 8));
        targetSkills = normalizeStrings(targetSkills).stream().limit(6).collect(Collectors.toList());

        List<Map<String, Object>> radar = new ArrayList<>();
        for (String skill : targetSkills) {
            int current = containsLike(currentSkills, skill) ? 78 : 35;
            int target = 85 + (containsLike(queryMarketSkills(null, 5), skill) ? 5 : 0);
            int gap = Math.max(0, target - current);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skill", skill);
            item.put("currentScore", current);
            item.put("targetScore", target);
            item.put("gapScore", gap);
            radar.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJobType", firstNonBlank(req.getTargetJobType(), inferTargetDirection(currentSkills)));
        result.put("skills", radar);
        result.put("interpretation", buildRadarInterpretation(radar));
        return result;
    }

    private Map<String, Object> buildResumeReviewFallback(ResumeReviewRequest req) {
        String resumeText = firstNonBlank(req.getResumeText(), "");
        List<String> currentSkills = normalizeStrings(req.getUserSkills());
        List<String> targetKeywords = inferTargetKeywords(currentSkills, req.getTargetJob());

        List<String> missingSkills = topMissingSkills(currentSkills, targetKeywords, 6);
        boolean hasOutcomeEvidence = containsOutcomeEvidence(resumeText);
        boolean hasProjectEvidence = containsProjectEvidence(resumeText);

        Map<String, Integer> scorecard = buildResumeScorecard(resumeText, currentSkills, targetKeywords);
        int totalScore = scorecard.values().stream().mapToInt(Integer::intValue).sum() / Math.max(1, scorecard.size());

        List<String> suggestions = new ArrayList<>();
        if (!hasOutcomeEvidence) {
            suggestions.add("Add quantified outcomes such as throughput, response time, conversion, or cost reduction.");
        }
        if (!hasProjectEvidence) {
            suggestions.add("Describe one complete project with your role, stack, architecture, and results.");
        }
        if (!missingSkills.isEmpty()) {
            suggestions.add("Add or learn target-role skills: " + String.join(", ", missingSkills) + ".");
        }
        if (!StringUtils.hasText(resumeText) || resumeText.length() < 120) {
            suggestions.add("Expand the resume summary. The current input is too short to reflect your experience.");
        }

        List<String> rewriteHints = new ArrayList<>();
        rewriteHints.add("Use 'Action + Method + Result' bullets instead of short phrases.");
        rewriteHints.add("Front-load target keywords such as " + String.join(", ", targetKeywords.stream().limit(4).collect(Collectors.toList())) + ".");
        rewriteHints.add("Separate projects, internships, and skills into distinct sections.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJob", req.getTargetJob());
        result.put("overallScore", totalScore);
        result.put("scorecard", scorecard);
        result.put("matchedSkills", currentSkills.stream().filter(skill -> containsLike(targetKeywords, skill)).collect(Collectors.toList()));
        result.put("missingSkills", missingSkills);
        result.put("suggestions", suggestions);
        result.put("rewriteHints", rewriteHints);
        result.put("summary", totalScore >= 75
                ? "The resume has a usable foundation, but targeted evidence can still improve conversion."
                : "The resume is not yet aligned with the target role. Strengthen evidence, structure, and keyword coverage.");
        return result;
    }

    private List<String> queryMarketSkills(Long jobId, int limit) {
        try {
            if (jobId != null) {
                return marketSkillService.cleanSkillNames(jobMapper.jobSkills(jobId), limit).stream()
                        .limit(limit)
                        .collect(Collectors.toList());
            }
            List<Map<String, Object>> rows = marketSkillService.topSkills(Math.max(6, limit));
            if (!rows.isEmpty()) {
                return rows.stream()
                        .map(row -> stringValue(row.get("skill")))
                        .filter(StringUtils::hasText)
                        .limit(limit)
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_SKILLS.stream().limit(limit).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryCareerPaths(String targetJob) {
        try {
            String sql = "SELECT job_title_from AS fromRole, job_title_to AS toRole, transition_type AS transitionType, "
                    + "avg_years AS avgYears, required_skills AS requiredSkills, frequency "
                    + "FROM biz_career_path "
                    + "WHERE (? IS NULL OR ? = '' OR job_title_to LIKE CONCAT('%', ?, '%') OR job_title_from LIKE CONCAT('%', ?, '%')) "
                    + "ORDER BY frequency DESC, avg_years ASC LIMIT 6";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, targetJob, targetJob, targetJob, targetJob);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> loadUserSkillNames(UserProfile profile) {
        List<String> profileSkills = parseStringList(profile == null ? null : profile.getSkills());
        if (!profileSkills.isEmpty()) {
            return profileSkills;
        }
        Long userId = currentUserId();
        if (userId != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> advisory = userInsightService.buildPlatformAdvisory(userId);
            Object userContext = advisory.get("userContext");
            if (userContext instanceof Map) {
                return asStringList(((Map<String, Object>) userContext).get("skills"));
            }
        }
        return new ArrayList<>();
    }

    private List<String> parseStringList(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) raw;
            return normalizeStrings(items.stream().map(this::stringValue).collect(Collectors.toList()));
        }
        String text = stringValue(raw);
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }
        try {
            if (text.trim().startsWith("[")) {
                List<String> values = objectMapper.readValue(text, new TypeReference<List<String>>() { });
                return normalizeStrings(values);
            }
        } catch (Exception ignored) {
        }
        return normalizeStrings(Arrays.asList(text.split("[,;|/\\n\\r]+")));
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String item = stringValue(value).trim();
            if (StringUtils.hasText(item)) {
                unique.add(item);
            }
        }
        return new ArrayList<>(unique);
    }

    private List<String> singletonIfText(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Collections.singletonList(value.trim());
    }

    private int safeLimit(Integer rawLimit, int defaultLimit, int maxLimit) {
        int limit = rawLimit == null ? defaultLimit : rawLimit;
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof Long ? (Long) principal : null;
    }

    private boolean containsLike(List<String> haystack, String needle) {
        if (!StringUtils.hasText(needle) || haystack == null) {
            return false;
        }
        String target = needle.toLowerCase(Locale.ROOT);
        return haystack.stream().anyMatch(item -> {
            String text = stringValue(item).toLowerCase(Locale.ROOT);
            return text.equals(target)
                    || text.contains(target)
                    || (text.length() > 4 && target.contains(text));
        });
    }

    private List<String> topMissingSkills(List<String> currentSkills, List<String> targetSkills, int limit) {
        List<String> missing = targetSkills.stream()
                .filter(skill -> !containsLike(currentSkills, skill))
                .collect(Collectors.toList());
        return missing.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> asStringList(Object raw) {
        return parseStringList(raw);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private String inferTargetDirection(List<String> skills) {
        String jobType = inferTargetJobTypeFromSkills(skills);
        return StringUtils.hasText(jobType) ? jobType : "Backend Engineer";
    }

    private List<String> inferTargetKeywords(List<String> skills, String hint) {
        Set<String> keywords = new LinkedHashSet<>();
        if (StringUtils.hasText(hint)) {
            String normalized = hint.toLowerCase(Locale.ROOT);
            if (normalized.contains("front")) {
                keywords.addAll(Arrays.asList("Vue", "React", "JavaScript", "TypeScript", "CSS"));
            } else if (normalized.contains("data") || normalized.contains("analysis")) {
                keywords.addAll(Arrays.asList("Python", "SQL", "ETL", "Pandas", "Visualization"));
            } else if (normalized.contains("test") || normalized.contains("qa")) {
                keywords.addAll(Arrays.asList("Test Case", "Automation", "Selenium", "JMeter", "Postman"));
            } else {
                keywords.addAll(Arrays.asList("Java", "Spring Boot", "MySQL", "Redis", "Docker"));
            }
        }
        keywords.addAll(normalizeStrings(skills));
        if (keywords.isEmpty()) {
            keywords.addAll(DEFAULT_SKILLS);
        }
        return new ArrayList<>(keywords);
    }

    private String inferTargetJobTypeFromSkills(List<String> skills) {
        Set<String> normalized = normalizeStrings(skills).stream()
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalized.contains("java") || normalized.contains("spring") || normalized.contains("spring boot")
                || normalized.contains("redis") || normalized.contains("mysql") || normalized.contains("docker")) {
            return "Backend Engineer";
        }
        if (normalized.contains("vue") || normalized.contains("react") || normalized.contains("javascript")
                || normalized.contains("typescript") || normalized.contains("css")) {
            return "Frontend Engineer";
        }
        if (normalized.contains("python") || normalized.contains("pandas") || normalized.contains("numpy")
                || normalized.contains("tableau") || normalized.contains("power bi")) {
            return "Data Analyst";
        }
        if (normalized.contains("linux") || normalized.contains("kubernetes") || normalized.contains("devops")) {
            return "Backend Engineer";
        }
        return "Backend Engineer";
    }

    private String inferCurrentRole(UserProfile profile, List<String> currentSkills) {
        if (profile != null && StringUtils.hasText(profile.getProfileSummary())) {
            return profile.getProfileSummary();
        }
        return inferTargetJobTypeFromSkills(currentSkills);
    }

    private int countKeywordMatches(List<String> source, List<String> keywords) {
        if (source == null || keywords == null) {
            return 0;
        }
        int count = 0;
        for (String keyword : keywords) {
            if (containsLike(source, keyword)) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesEducation(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        String e = expected.toLowerCase(Locale.ROOT);
        String a = actual.toLowerCase(Locale.ROOT);
        if (a.contains(e) || e.contains(a)) {
            return true;
        }
        if (e.contains("bachelor") && (a.contains("bachelor") || a.contains("undergraduate"))) {
            return true;
        }
        return e.contains("master") && a.contains("master");
    }

    private boolean matchesExperience(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        String e = expected.toLowerCase(Locale.ROOT);
        String a = actual.toLowerCase(Locale.ROOT);
        return a.contains(e) || e.contains(a)
                || (e.contains("1") && a.contains("1"))
                || (e.contains("3") && a.contains("3"))
                || (e.contains("5") && a.contains("5"));
    }

    private double recencyScore(LocalDate publishDate) {
        if (publishDate == null) {
            return 0.0;
        }
        long days = ChronoUnit.DAYS.between(publishDate, LocalDate.now());
        if (days <= 3) {
            return 12.0;
        }
        if (days <= 7) {
            return 8.0;
        }
        if (days <= 15) {
            return 5.0;
        }
        return 2.0;
    }

    private List<String> buildWhyMatched(JobRecommendRequest req, JobPosting job, List<String> jobSkills, int skillMatches) {
        List<String> reasons = new ArrayList<>();
        if (skillMatches > 0) {
            reasons.add("Matched " + skillMatches + " skill keywords from your profile.");
        }
        if (containsLike(req.getPreferredCities(), job.getCity())) {
            reasons.add("The job is in one of your preferred cities.");
        }
        if (matchesEducation(req.getEducation(), job.getEducation())) {
            reasons.add("Education requirement is aligned.");
        }
        if (matchesExperience(req.getExperience(), job.getExperience())) {
            reasons.add("Experience requirement is close to your target.");
        }
        if (!jobSkills.isEmpty()) {
            reasons.add("Market stack includes " + String.join(", ", jobSkills.stream().limit(4).collect(Collectors.toList())) + ".");
        }
        if (reasons.isEmpty()) {
            reasons.add("Selected for strong market freshness and baseline profile fit.");
        }
        return reasons;
    }

    private List<String> buildJobNextActions(JobRecommendRequest req, JobPosting job, List<String> jobSkills) {
        List<String> actions = new ArrayList<>();
        List<String> missing = topMissingSkills(normalizeStrings(req.getSkills()), jobSkills, 3);
        if (!missing.isEmpty()) {
            actions.add("补齐技能: " + String.join(", ", missing));
        }
        actions.add("根据岗位关键词改写简历标题和项目经历");
        if (StringUtils.hasText(job.getCity()) && !containsLike(req.getPreferredCities(), job.getCity())) {
            actions.add("确认是否接受 " + job.getCity() + " 的岗位机会");
        }
        return actions;
    }

    private String fitLabel(double score) {
        if (score >= 70) {
            return "High";
        }
        if (score >= 50) {
            return "Medium";
        }
        return "Explore";
    }

    private Map<String, Object> buildRecommendProfileSnapshot(JobRecommendRequest req) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("skills", req.getSkills());
        profile.put("preferredCities", req.getPreferredCities());
        profile.put("education", req.getEducation());
        profile.put("experience", req.getExperience());
        profile.put("salaryMin", req.getSalaryMin());
        profile.put("salaryMax", req.getSalaryMax());
        profile.put("industry", req.getIndustry());
        return profile;
    }

    private Map<String, Object> buildJobRecommendationSummary(JobRecommendRequest req, List<Map<String, Object>> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requestedLimit", req.getLimit());
        summary.put("returnedCount", items.size());
        summary.put("topCities", items.stream().map(item -> stringValue(item.get("city"))).filter(StringUtils::hasText).distinct().limit(3).collect(Collectors.toList()));
        summary.put("topIndustries", items.stream().map(item -> stringValue(item.get("industryName"))).filter(StringUtils::hasText).distinct().limit(3).collect(Collectors.toList()));
        summary.put("avgScore", items.isEmpty() ? 0 : items.stream().mapToDouble(item -> ((Number) item.get("score")).doubleValue()).average().orElse(0));
        return summary;
    }

    private List<Map<String, Object>> buildLearningPath(List<String> missingSkills) {
        List<Map<String, Object>> stages = new ArrayList<>();
        int month = 1;
        for (String skill : missingSkills.stream().limit(4).collect(Collectors.toList())) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stage", "Month " + month);
            stage.put("focusSkill", skill);
            stage.put("goal", "Build one portfolio-ready practice around " + skill + ".");
            stage.put("deliverable", "Project demo + resume bullet");
            stages.add(stage);
            month++;
        }
        return stages;
    }

    private List<String> suggestEntryStrengths(List<String> currentSkills) {
        List<String> strengths = new ArrayList<>();
        if (containsLike(currentSkills, "Java") || containsLike(currentSkills, "Spring")) {
            strengths.add("Backend engineering foundation");
        }
        if (containsLike(currentSkills, "MySQL") || containsLike(currentSkills, "SQL")) {
            strengths.add("Data modeling and query basics");
        }
        if (containsLike(currentSkills, "Vue") || containsLike(currentSkills, "React")) {
            strengths.add("UI collaboration capability");
        }
        if (strengths.isEmpty()) {
            strengths.add("General software learning ability");
        }
        return strengths;
    }

    private List<Map<String, Object>> buildSyntheticCareerPath(CareerPathRequest req) {
        List<Map<String, Object>> path = new ArrayList<>();
        path.add(createCareerStep(req.getCurrentJob(), "Advanced " + req.getCurrentJob(), "UPSKILL", 1.0, req.getCurrentSkills()));
        path.add(createCareerStep("Advanced " + req.getCurrentJob(), req.getTargetJob(), "TRANSITION", 1.5,
                inferTargetKeywords(req.getCurrentSkills(), req.getTargetJob())));
        return path;
    }

    private Map<String, Object> createCareerStep(String from, String to, String transitionType, double avgYears, List<String> skills) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("fromRole", from);
        step.put("toRole", to);
        step.put("transitionType", transitionType);
        step.put("avgYears", avgYears);
        step.put("requiredSkills", normalizeStrings(skills).stream().limit(6).collect(Collectors.toList()));
        return step;
    }

    private List<Map<String, Object>> buildCareerMilestones(CareerPathRequest req, List<Map<String, Object>> steps) {
        List<Map<String, Object>> milestones = new ArrayList<>();
        int quarter = 1;
        for (Map<String, Object> step : steps) {
            Map<String, Object> milestone = new LinkedHashMap<>();
            milestone.put("quarter", "Q" + quarter);
            milestone.put("goal", "Prepare for " + stringValue(step.get("toRole")));
            milestone.put("focus", parseStringList(step.get("requiredSkills")).stream().limit(3).collect(Collectors.toList()));
            milestone.put("evidence", "One project, one optimized resume section, one interview story");
            milestones.add(milestone);
            quarter++;
        }
        return milestones;
    }

    private List<Map<String, Object>> buildRecommendedProjects(CareerPathRequest req) {
        List<String> targetKeywords = inferTargetKeywords(req.getCurrentSkills(), req.getTargetJob());
        List<Map<String, Object>> projects = new ArrayList<>();

        Map<String, Object> project1 = new LinkedHashMap<>();
        project1.put("name", req.getTargetJob() + " portfolio project");
        project1.put("goal", "Show the stack most frequently required by your target role.");
        project1.put("stack", targetKeywords.stream().limit(4).collect(Collectors.toList()));
        projects.add(project1);

        Map<String, Object> project2 = new LinkedHashMap<>();
        project2.put("name", "Data and metrics dashboard");
        project2.put("goal", "Demonstrate business understanding, data modeling, and reporting.");
        project2.put("stack", Arrays.asList("SQL", "API", "Visualization"));
        projects.add(project2);

        return projects;
    }

    private String buildRadarInterpretation(List<Map<String, Object>> radar) {
        List<String> weakAreas = radar.stream()
                .filter(item -> ((Number) item.get("gapScore")).intValue() >= 30)
                .map(item -> stringValue(item.get("skill")))
                .collect(Collectors.toList());
        if (weakAreas.isEmpty()) {
            return "Your current skill distribution is close to the target role. Move focus to projects and interview stories.";
        }
        return "The biggest gaps are in " + String.join(", ", weakAreas) + ". These should be your first upskilling targets.";
    }

    private boolean containsOutcomeEvidence(String text) {
        String normalized = firstNonBlank(text, "").toLowerCase(Locale.ROOT);
        return normalized.matches(".*\\d+.*")
                || normalized.contains("%")
                || normalized.contains("increase")
                || normalized.contains("reduced")
                || normalized.contains("improved");
    }

    private boolean containsProjectEvidence(String text) {
        String normalized = firstNonBlank(text, "").toLowerCase(Locale.ROOT);
        return normalized.contains("project")
                || normalized.contains("system")
                || normalized.contains("platform")
                || normalized.contains("module")
                || normalized.contains("负责");
    }

    private Map<String, Integer> buildResumeScorecard(String resumeText, List<String> currentSkills, List<String> targetKeywords) {
        Map<String, Integer> scorecard = new LinkedHashMap<>();
        scorecard.put("targeting", scoreBinary(StringUtils.hasText(resumeText) && countKeywordMatches(currentSkills, targetKeywords) >= 2, 78, 42));
        scorecard.put("keywords", scoreBinary(countKeywordMatches(currentSkills, targetKeywords) >= 3, 80, 46));
        scorecard.put("projectEvidence", scoreBinary(containsProjectEvidence(resumeText), 82, 38));
        scorecard.put("businessImpact", scoreBinary(containsOutcomeEvidence(resumeText), 84, 35));
        scorecard.put("readability", scoreBinary(StringUtils.hasText(resumeText) && resumeText.length() >= 180, 76, 40));
        return scorecard;
    }

    private int scoreBinary(boolean ok, int passScore, int failScore) {
        return ok ? passScore : failScore;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Map<String, Object> buildPlanSummary(Map<String, Object> jobs, Map<String, Object> gaps) {
        Map<String, Object> summary = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) jobs.getOrDefault("items", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<String> missingSkills = (List<String>) gaps.getOrDefault("prioritySkills", Collections.emptyList());
        summary.put("headline", items.isEmpty()
                ? "No strong matches yet. Close the skill gap first."
                : "Start with the top recommended jobs and close the highest-priority skill gaps.");
        summary.put("topJobTitles", items.stream().map(item -> stringValue(item.get("title"))).limit(3).collect(Collectors.toList()));
        summary.put("prioritySkills", missingSkills.stream().limit(4).collect(Collectors.toList()));
        summary.put("nextStep", missingSkills.isEmpty()
                ? "Refresh your resume and apply to the top matches."
                : "Learn the priority skills, add one proof project, then re-run recommendations.");
        return summary;
    }
}
