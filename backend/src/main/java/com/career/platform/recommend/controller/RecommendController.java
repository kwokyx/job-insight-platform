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

    public RecommendController(JobPostingMapper jobMapper, WebClient algorithmWebClient,
                               UserProfileMapper userProfileMapper, JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper, MarketSkillService marketSkillService,
                               UserInsightService userInsightService) {
        this.jobMapper = jobMapper;
        this.algorithmWebClient = algorithmWebClient;
        this.userProfileMapper = userProfileMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.marketSkillService = marketSkillService;
        this.userInsightService = userInsightService;
    }

    public static class JobRecommendRequest {
        private List<String> skills = Collections.emptyList();
        private List<String> coreSkills = Collections.emptyList();
        private List<String> preferredCities = Collections.emptyList();
        private List<String> excludedKeywords = Collections.emptyList();
        private List<String> preferredCompanySizes = Collections.emptyList();
        private List<String> preferredFinanceStages = Collections.emptyList();
        private String targetJobType;
        private String education;
        private String experience;
        private Double experienceYears;
        private Double salaryMin;
        private Double salaryMax;
        private String industry;
        private Integer limit = 20;

        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
        public List<String> getCoreSkills() { return coreSkills; }
        public void setCoreSkills(List<String> coreSkills) { this.coreSkills = coreSkills; }
        public List<String> getPreferredCities() { return preferredCities; }
        public void setPreferredCities(List<String> preferredCities) { this.preferredCities = preferredCities; }
        public List<String> getExcludedKeywords() { return excludedKeywords; }
        public void setExcludedKeywords(List<String> excludedKeywords) { this.excludedKeywords = excludedKeywords; }
        public List<String> getPreferredCompanySizes() { return preferredCompanySizes; }
        public void setPreferredCompanySizes(List<String> preferredCompanySizes) { this.preferredCompanySizes = preferredCompanySizes; }
        public List<String> getPreferredFinanceStages() { return preferredFinanceStages; }
        public void setPreferredFinanceStages(List<String> preferredFinanceStages) { this.preferredFinanceStages = preferredFinanceStages; }
        public String getTargetJobType() { return targetJobType; }
        public void setTargetJobType(String targetJobType) { this.targetJobType = targetJobType; }
        public String getEducation() { return education; }
        public void setEducation(String education) { this.education = education; }
        public String getExperience() { return experience; }
        public void setExperience(String experience) { this.experience = experience; }
        public Double getExperienceYears() { return experienceYears; }
        public void setExperienceYears(Double experienceYears) { this.experienceYears = experienceYears; }
        public Double getSalaryMin() { return salaryMin; }
        public void setSalaryMin(Double salaryMin) { this.salaryMin = salaryMin; }
        public Double getSalaryMax() { return salaryMax; }
        public void setSalaryMax(Double salaryMax) { this.salaryMax = salaryMax; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class SkillAdviceRequest {
        private List<String> userSkills = Collections.emptyList();
        private String targetJobType;
        private String city;

        public List<String> getUserSkills() { return userSkills; }
        public void setUserSkills(List<String> userSkills) { this.userSkills = userSkills; }
        public String getTargetJobType() { return targetJobType; }
        public void setTargetJobType(String targetJobType) { this.targetJobType = targetJobType; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    public static class CareerPathRequest {
        private String currentJob;
        private String targetJob;
        private List<String> currentSkills = Collections.emptyList();
        private String city;

        public String getCurrentJob() { return currentJob; }
        public void setCurrentJob(String currentJob) { this.currentJob = currentJob; }
        public String getTargetJob() { return targetJob; }
        public void setTargetJob(String targetJob) { this.targetJob = targetJob; }
        public List<String> getCurrentSkills() { return currentSkills; }
        public void setCurrentSkills(List<String> currentSkills) { this.currentSkills = currentSkills; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    public static class ResumeReviewRequest {
        private String targetJob;
        private String resumeText;
        private List<String> userSkills = Collections.emptyList();

        public String getTargetJob() { return targetJob; }
        public void setTargetJob(String targetJob) { this.targetJob = targetJob; }
        public String getResumeText() { return resumeText; }
        public void setResumeText(String resumeText) { this.resumeText = resumeText; }
        public List<String> getUserSkills() { return userSkills; }
        public void setUserSkills(List<String> userSkills) { this.userSkills = userSkills; }
    }

    @Log("Recommend jobs")
    @Operation(summary = "Recommend jobs")
    @PostMapping("/jobs")
    public R<?> recommendJobs(@RequestBody JobRecommendRequest req) {
        JobRecommendRequest normalized = enrichJobRequest(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("skills", normalized.getSkills());
            params.put("core_skills", normalized.getCoreSkills());
            params.put("preferred_cities", normalized.getPreferredCities());
            params.put("excluded_keywords", normalized.getExcludedKeywords());
            params.put("preferred_company_sizes", normalized.getPreferredCompanySizes());
            params.put("preferred_finance_stages", normalized.getPreferredFinanceStages());
            params.put("target_job_type", normalized.getTargetJobType());
            params.put("education", normalized.getEducation());
            params.put("experience", normalized.getExperience());
            params.put("experience_years", normalized.getExperienceYears());
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

    @Log("Job ranker status")
    @Operation(summary = "Job ranker status")
    @GetMapping("/ranker-status")
    public R<?> rankerStatus() {
        try {
            Object result = algorithmWebClient.get()
                    .uri("/algorithm/match/ranker-status")
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("trained", false);
            fallback.put("sample_count", 0);
            fallback.put("feature_names", Collections.emptyList());
            fallback.put("model_type", "unavailable");
            fallback.put("source", "unavailable");
            fallback.put("message", "算法服务暂不可用，当前无法读取排序模型状态。");
            return R.ok(fallback);
        }
    }

    @Log("Train job ranker")
    @Operation(summary = "Train job ranker")
    @PostMapping("/train-ranker")
    public R<?> trainRanker(@RequestParam(defaultValue = "20000") Integer limit) {
        try {
            Object result = algorithmWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/algorithm/match/train-ranker")
                            .queryParam("limit", safeLimit(limit, 20000, 100000))
                            .build())
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofMinutes(3))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            throw new BusinessException("职位排序模型训练失败，请检查算法服务和数据库连接");
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
            if (normalized.getCoreSkills() == null || normalized.getCoreSkills().isEmpty()) {
                normalized.setCoreSkills(normalized.getSkills().stream().limit(3).collect(Collectors.toList()));
            } else {
                normalized.setCoreSkills(normalizeStrings(normalized.getCoreSkills()));
            }
            if ((normalized.getPreferredCities() == null || normalized.getPreferredCities().isEmpty())
                    && StringUtils.hasText(profile.getTargetCityCode())) {
                normalized.setPreferredCities(singletonIfText(profile.getTargetCityCode()));
            } else {
                normalized.setPreferredCities(normalizeStrings(normalized.getPreferredCities()));
            }
            normalized.setExcludedKeywords(normalizeStrings(normalized.getExcludedKeywords()));
            normalized.setPreferredCompanySizes(normalizeStrings(normalized.getPreferredCompanySizes()));
            normalized.setPreferredFinanceStages(normalizeStrings(normalized.getPreferredFinanceStages()));
            if (!StringUtils.hasText(normalized.getEducation())) {
                normalized.setEducation(profile.getEducationLevel());
            }
            if (!StringUtils.hasText(normalized.getTargetJobType())) {
                normalized.setTargetJobType(firstNonBlank(profile.getProfileSummary(), inferTargetDirection(normalized.getSkills())));
            }
            if (normalized.getSalaryMin() == null && profile.getExpectedSalaryMin() != null) {
                normalized.setSalaryMin(profile.getExpectedSalaryMin().doubleValue());
            }
            if (normalized.getSalaryMax() == null && profile.getExpectedSalaryMax() != null) {
                normalized.setSalaryMax(profile.getExpectedSalaryMax().doubleValue());
            }
        } else {
            normalized.setSkills(normalizeStrings(normalized.getSkills()));
            normalized.setCoreSkills(normalizeStrings(normalized.getCoreSkills()));
            normalized.setPreferredCities(normalizeStrings(normalized.getPreferredCities()));
            normalized.setExcludedKeywords(normalizeStrings(normalized.getExcludedKeywords()));
            normalized.setPreferredCompanySizes(normalizeStrings(normalized.getPreferredCompanySizes()));
            normalized.setPreferredFinanceStages(normalizeStrings(normalized.getPreferredFinanceStages()));
        }
        if (normalized.getCoreSkills() == null || normalized.getCoreSkills().isEmpty()) {
            normalized.setCoreSkills(normalized.getSkills().stream().limit(3).collect(Collectors.toList()));
        }
        if (!StringUtils.hasText(normalized.getTargetJobType())) {
            normalized.setTargetJobType(inferTargetDirection(normalized.getSkills()));
        }
        if (normalized.getExperienceYears() == null && StringUtils.hasText(normalized.getExperience())) {
            normalized.setExperienceYears(experienceYears(normalized.getExperience()));
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
        List<String> coreSkills = normalizeStrings(req.getCoreSkills());
        List<String> desiredCities = normalizeStrings(req.getPreferredCities());
        List<String> excludedKeywords = normalizeStrings(req.getExcludedKeywords());
        List<String> preferredCompanySizes = normalizeStrings(req.getPreferredCompanySizes());
        List<String> preferredFinanceStages = normalizeStrings(req.getPreferredFinanceStages());
        String roleHint = firstNonBlank(req.getTargetJobType(), req.getIndustry());
        String inferredFamily = inferJobFamily(desiredSkills, roleHint);
        List<String> keywords = inferTargetKeywords(desiredSkills, roleHint);
        List<String> roleKeywords = extractSearchTokens(roleHint);
        List<String> domainKeywords = inferDomainKeywords(desiredSkills);
        List<String> familyPositiveKeywords = familyPositiveKeywords(inferredFamily);
        List<String> familyNegativeKeywords = new ArrayList<>(normalizeStrings(excludedKeywords));
        familyNegativeKeywords.addAll(defaultNegativeKeywords(inferredFamily));
        familyNegativeKeywords = normalizeStrings(familyNegativeKeywords);
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
            int coreSkillMatches = countKeywordMatches(jobSkills, coreSkills);
            List<String> titleSearchSpace = Arrays.asList(
                    firstNonBlank(job.getTitle(), ""),
                    firstNonBlank(job.getIndustryName(), ""),
                    firstNonBlank(job.getDescription(), "")
            );
            int titleMatches = countKeywordMatches(titleSearchSpace, keywords);
            double roleAlignment = roleKeywords.isEmpty() ? 0D : textMatchScore(
                    firstNonBlank(job.getTitle(), "") + " " + firstNonBlank(job.getDescription(), ""),
                    roleKeywords
            );
            double domainAlignment = domainKeywords.isEmpty() ? 0D : textMatchScore(
                    firstNonBlank(job.getTitle(), "")
                            + " "
                            + firstNonBlank(job.getDescription(), "")
                            + " "
                            + firstNonBlank(job.getIndustryName(), "")
                            + " "
                            + String.join(" ", normalizeStrings(Arrays.asList(job.getJobLabels()))),
                    domainKeywords
            );
            String searchableText = firstNonBlank(job.getTitle(), "")
                    + " "
                    + firstNonBlank(job.getDescription(), "")
                    + " "
                    + firstNonBlank(job.getIndustryName(), "")
                    + " "
                    + firstNonBlank(job.getJobLabels(), "");
            double familyAlignment = familyPositiveKeywords.isEmpty() ? 0D : textMatchScore(searchableText, familyPositiveKeywords);
            double score = 0.0;
            score += familyAlignment * 28.0;
            score += roleAlignment * 30.0;
            score += Math.min(28, coreSkillMatches * 9.0);
            score += desiredSkills.isEmpty() ? 8 : Math.min(18, skillMatches * 4.5);
            score += Math.min(12, titleMatches * 4.0);
            score += domainAlignment * 14.0;
            if (containsLike(desiredCities, job.getCity())) {
                score += 15;
            }
            if (StringUtils.hasText(req.getIndustry())
                    && containsLike(singletonIfText(req.getIndustry()), firstNonBlank(job.getIndustryName(), job.getTitle()))) {
                score += 10;
            }
            if (containsLike(preferredCompanySizes, job.getCompanySize())) {
                score += 6;
            }
            if (containsLike(preferredFinanceStages, job.getCompanyFinance())) {
                score += 4;
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

            String normalizedSearchableText = searchableText.toLowerCase(Locale.ROOT);
            if (familyNegativeKeywords.stream().anyMatch(keyword -> normalizedSearchableText.contains(keyword.toLowerCase(Locale.ROOT)))) {
                continue;
            }
            if (StringUtils.hasText(req.getTargetJobType()) && roleAlignment < 0.2D && titleMatches == 0) {
                continue;
            }
            if (StringUtils.hasText(inferredFamily)
                    && !"general".equalsIgnoreCase(inferredFamily)
                    && familyAlignment < 0.2D
                    && coreSkillMatches == 0
                    && skillMatches == 0
                    && domainAlignment < 0.2D) {
                continue;
            }
            if (!coreSkills.isEmpty() && coreSkillMatches == 0) {
                continue;
            }
            if (!desiredSkills.isEmpty() && !domainKeywords.isEmpty() && domainAlignment == 0D && skillMatches == 0) {
                continue;
            }
            if (!desiredSkills.isEmpty() && skillMatches == 0 && titleMatches == 0 && roleAlignment < 0.2D) {
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
            item.put("matchedCoreSkills", coreSkills.stream()
                    .filter(skill -> containsLike(jobSkills, skill))
                    .collect(Collectors.toList()));
            item.put("jobSkills", jobSkills);
            item.put("jobFamily", inferredFamily);
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
                ? "你当前与目标岗位的核心技能重合度还偏低，建议先补齐优先级最高的技能缺口，再进入集中投递阶段。"
                : "你已经具备部分核心基础，下一步重点是围绕缺口技能和项目证明继续提升匹配度。");
        result.put("diagnosis", buildSkillGapDiagnosis(req, matchedSkills, missingSkills, marketSkills, ratio));
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
                Locale.CHINA,
                "预计需要经过 %d 个阶段，整体转型周期大约 %d 到 %d 个月。",
                steps.size(),
                Math.max(6, steps.size() * 3),
                Math.max(9, steps.size() * 5)
        ));
        result.put("strategySummary", buildCareerStrategySummary(req, steps));
        return result;
    }

    private Map<String, Object> buildSkillRadarFallback(SkillAdviceRequest req) {
        List<String> currentSkills = normalizeStrings(req.getUserSkills());
        List<String> targetSkills = inferTargetKeywords(currentSkills, req.getTargetJobType());
        
        List<Map<String, Object>> marketTop = marketSkillService.topSkills(30);
        List<String> marketSkillNames = marketTop.stream()
                .map(m -> String.valueOf(m.get("skill")))
                .collect(Collectors.toList());
                
        targetSkills.addAll(marketSkillNames.stream().limit(8).collect(Collectors.toList()));
        targetSkills = normalizeStrings(targetSkills).stream().limit(6).collect(Collectors.toList());

        List<Map<String, Object>> radar = new ArrayList<>();
        double maxCount = marketTop.isEmpty() ? 100D : readDouble(marketTop.get(0).get("count"), 100D);
        if (maxCount <= 0) maxCount = 100D;

        for (String skill : targetSkills) {
            double freqScore = 50; 
            for (Map<String, Object> m : marketTop) {
                if (skill.equalsIgnoreCase(String.valueOf(m.get("skill")))) {
                    double count = readDouble(m.get("count"), 0D);
                    freqScore = 50 + (count / maxCount) * 50; // 50 to 100
                    break;
                }
            }
            
            boolean userHas = containsLike(currentSkills, skill);
            int current = userHas ? (int)(freqScore * (0.8 + Math.random()*0.15)) : (int)(freqScore * (0.3 + Math.random()*0.15));
            int target = (int)Math.min(100, freqScore + (Math.random() * 5));
            if (current > target) target = Math.min(100, current + 5);
            
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
    
    private double readDouble(Object value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
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
            suggestions.add("补充可量化成果，例如性能提升、响应时间下降、转化提升或成本优化。");
        }
        if (!hasProjectEvidence) {
            suggestions.add("至少写清一个完整项目，包括你的职责、技术栈、架构设计和最终结果。");
        }
        if (!missingSkills.isEmpty()) {
            suggestions.add("优先补齐目标岗位高频技能：" + String.join("、", missingSkills) + "。");
        }
        if (!StringUtils.hasText(resumeText) || resumeText.length() < 120) {
            suggestions.add("补充简历摘要与经历描述，当前内容过短，无法有效体现你的能力层次。");
        }

        List<String> rewriteHints = new ArrayList<>();
        rewriteHints.add("用“动作 + 方法 + 结果”的表达方式替代简单短语堆砌。");
        rewriteHints.add("把 " + String.join("、", targetKeywords.stream().limit(4).collect(Collectors.toList())) + " 这类目标关键词前置。");
        rewriteHints.add("把项目经历、实习经历、技能清单拆成独立模块，避免信息混杂。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetJob", req.getTargetJob());
        result.put("overallScore", totalScore);
        result.put("scorecard", scorecard);
        result.put("matchedSkills", currentSkills.stream().filter(skill -> containsLike(targetKeywords, skill)).collect(Collectors.toList()));
        result.put("missingSkills", missingSkills);
        result.put("suggestions", suggestions);
        result.put("rewriteHints", rewriteHints);
        result.put("summary", totalScore >= 75
                ? "你的简历已经具备投递基础，但还需要增强成果证据和目标岗位关键词，才能进一步提高转化率。"
                : "你的简历与目标岗位的贴合度还不够，当前最需要强化的是项目证据、结构表达和关键词覆盖。");
        result.put("diagnosis", buildResumeDiagnosis(totalScore, suggestions, missingSkills, targetKeywords));
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

    private String inferJobFamily(List<String> skills, String hint) {
        String text = (String.join(" ", normalizeStrings(skills)) + " " + firstNonBlank(hint, "")).toLowerCase(Locale.ROOT);
        if (containsAny(text, Arrays.asList("java", "spring", "spring boot", "backend", "后端", "服务端", "golang", "微服务"))) {
            return "backend";
        }
        if (containsAny(text, Arrays.asList("vue", "react", "frontend", "前端", "javascript", "typescript"))) {
            return "frontend";
        }
        if (containsAny(text, Arrays.asList("python", "data", "analysis", "analyst", "数据", "sql", "bi"))) {
            return "data";
        }
        if (containsAny(text, Arrays.asList("qa", "test", "测试", "自动化测试"))) {
            return "qa";
        }
        return "general";
    }

    private List<String> familyPositiveKeywords(String family) {
        switch (firstNonBlank(family, "general").toLowerCase(Locale.ROOT)) {
            case "backend":
                return Arrays.asList("后端", "开发", "研发", "工程师", "java", "spring", "backend", "software", "服务端", "系统");
            case "frontend":
                return Arrays.asList("前端", "开发", "工程师", "vue", "react", "frontend", "web", "javascript");
            case "data":
                return Arrays.asList("数据", "分析", "data", "analyst", "python", "sql", "bi", "算法");
            case "qa":
                return Arrays.asList("测试", "qa", "test", "质量", "自动化");
            default:
                return Collections.emptyList();
        }
    }

    private List<String> defaultNegativeKeywords(String family) {
        if (!Arrays.asList("backend", "frontend", "data", "qa").contains(firstNonBlank(family, "").toLowerCase(Locale.ROOT))) {
            return Collections.emptyList();
        }
        return Arrays.asList(
                "美容", "美妆", "顾问", "钳工", "普工", "导购", "招商主管", "招商",
                "销售", "客服", "学徒", "店员", "收银", "主播", "直播", "置业", "房产"
        );
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return keywords.stream()
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
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

    private List<String> extractSearchTokens(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split("[,;|/\\s\\-()（）]+"))
                .map(this::stringValue)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(token -> token.length() > 1)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> inferDomainKeywords(List<String> skills) {
        Set<String> normalized = normalizeStrings(skills).stream()
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (!Collections.disjoint(normalized, Arrays.asList("java", "spring", "spring boot", "mysql", "redis", "docker", "git", "linux"))) {
            keywords.addAll(Arrays.asList("java", "spring", "backend", "后端", "开发", "engineer", "software", "程序员", "研发", "mysql", "服务端", "系统"));
        }
        if (!Collections.disjoint(normalized, Arrays.asList("vue", "react", "javascript", "typescript", "css", "html"))) {
            keywords.addAll(Arrays.asList("frontend", "front-end", "前端", "vue", "react", "web", "javascript"));
        }
        if (!Collections.disjoint(normalized, Arrays.asList("python", "pandas", "numpy", "sql", "tableau", "power bi"))) {
            keywords.addAll(Arrays.asList("data", "analysis", "analyst", "数据", "python", "sql", "bi"));
        }
        return new ArrayList<>(keywords);
    }

    private double textMatchScore(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return 0D;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        long matches = keywords.stream()
                .map(this::stringValue)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(normalized::contains)
                .count();
        return matches / (double) keywords.size();
    }

    private double experienceYears(String experience) {
        if (!StringUtils.hasText(experience)) {
            return 0D;
        }
        List<Integer> nums = Arrays.stream(experience.replaceAll("[^0-9]+", " ").trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(value -> value != null)
                .collect(Collectors.toList());
        if (nums.isEmpty()) {
            return 0D;
        }
        return nums.stream().mapToInt(Integer::intValue).average().orElse(0D);
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
        if (StringUtils.hasText(req.getTargetJobType())
                && textMatchScore(firstNonBlank(job.getTitle(), "") + " " + firstNonBlank(job.getDescription(), ""),
                extractSearchTokens(req.getTargetJobType())) >= 0.5D) {
            reasons.add("Job title and responsibilities are close to your target role.");
        }
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
        profile.put("coreSkills", req.getCoreSkills());
        profile.put("preferredCities", req.getPreferredCities());
        profile.put("excludedKeywords", req.getExcludedKeywords());
        profile.put("preferredCompanySizes", req.getPreferredCompanySizes());
        profile.put("preferredFinanceStages", req.getPreferredFinanceStages());
        profile.put("targetJobType", req.getTargetJobType());
        profile.put("education", req.getEducation());
        profile.put("experience", req.getExperience());
        profile.put("experienceYears", req.getExperienceYears());
        profile.put("salaryMin", req.getSalaryMin());
        profile.put("salaryMax", req.getSalaryMax());
        profile.put("industry", req.getIndustry());
        return profile;
    }

    private Map<String, Object> buildJobRecommendationSummary(JobRecommendRequest req, List<Map<String, Object>> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<String> cities = items.stream()
                .map(item -> stringValue(item.get("city")))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
        List<String> industries = items.stream()
                .map(item -> stringValue(item.get("industryName")))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
        double avgScore = items.isEmpty() ? 0 : items.stream()
                .mapToDouble(item -> ((Number) item.get("score")).doubleValue())
                .average()
                .orElse(0);
        List<String> topSkills = items.stream()
                .flatMap(item -> parseStringList(item.get("matchedSkills")).stream())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        summary.put("requestedLimit", req.getLimit());
        summary.put("returnedCount", items.size());
        summary.put("topCities", cities);
        summary.put("topIndustries", industries);
        summary.put("avgScore", avgScore);
        summary.put("marketDiagnosis", items.isEmpty()
                ? "当前没有形成足够强的岗位匹配结果，建议先补齐目标岗位核心技能并完善画像。"
                : "当前推荐结果主要集中在 "
                + (cities.isEmpty() ? "核心招聘城市" : String.join("、", cities))
                + "，岗位方向偏向 "
                + (industries.isEmpty() ? "通用互联网/数字化岗位" : String.join("、", industries))
                + "，整体匹配度约为 "
                + String.format(Locale.CHINA, "%.1f", avgScore)
                + " 分。");
        summary.put("applicationStrategy", items.isEmpty()
                ? "先完成技能补齐与简历针对性优化，再重新生成推荐。"
                : "优先投递前 5 个高分岗位，同时围绕 "
                + (topSkills.isEmpty() ? "岗位关键词" : String.join("、", topSkills))
                + " 调整简历标题、项目描述与面试故事。");
        return summary;
    }

    private List<Map<String, Object>> buildLearningPath(List<String> missingSkills) {
        List<Map<String, Object>> stages = new ArrayList<>();
        int month = 1;
        for (String skill : missingSkills.stream().limit(4).collect(Collectors.toList())) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stage", "第 " + month + " 月");
            stage.put("focusSkill", skill);
            stage.put("goal", "围绕 " + skill + " 做出一个可写进简历、可展示给面试官的项目练习。");
            stage.put("deliverable", "项目演示 + 简历成果描述 + 面试表达素材");
            stages.add(stage);
            month++;
        }
        return stages;
    }

    private List<String> suggestEntryStrengths(List<String> currentSkills) {
        List<String> strengths = new ArrayList<>();
        if (containsLike(currentSkills, "Java") || containsLike(currentSkills, "Spring")) {
            strengths.add("具备后端开发基础");
        }
        if (containsLike(currentSkills, "MySQL") || containsLike(currentSkills, "SQL")) {
            strengths.add("具备数据建模与 SQL 基础");
        }
        if (containsLike(currentSkills, "Vue") || containsLike(currentSkills, "React")) {
            strengths.add("具备前端协作与界面理解能力");
        }
        if (strengths.isEmpty()) {
            strengths.add("具备通用软件学习与迁移能力");
        }
        return strengths;
    }

    private List<Map<String, Object>> buildSyntheticCareerPath(CareerPathRequest req) {
        List<Map<String, Object>> path = new ArrayList<>();
        path.add(createCareerStep(req.getCurrentJob(), req.getCurrentJob() + " 进阶阶段", "UPSKILL", 1.0, req.getCurrentSkills()));
        path.add(createCareerStep(req.getCurrentJob() + " 进阶阶段", req.getTargetJob(), "TRANSITION", 1.5,
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
            milestone.put("quarter", "第 " + quarter + " 阶段");
            milestone.put("goal", "为转向 " + stringValue(step.get("toRole")) + " 做准备");
            milestone.put("focus", parseStringList(step.get("requiredSkills")).stream().limit(3).collect(Collectors.toList()));
            milestone.put("evidence", "至少完成 1 个项目案例、1 版定向简历优化和 1 组面试表达素材");
            milestones.add(milestone);
            quarter++;
        }
        return milestones;
    }

    private List<Map<String, Object>> buildRecommendedProjects(CareerPathRequest req) {
        List<String> targetKeywords = inferTargetKeywords(req.getCurrentSkills(), req.getTargetJob());
        List<Map<String, Object>> projects = new ArrayList<>();

        Map<String, Object> project1 = new LinkedHashMap<>();
        project1.put("name", req.getTargetJob() + " 定向作品项目");
        project1.put("goal", "用一个完整项目证明你具备目标岗位最常见的核心技术栈与交付能力。");
        project1.put("stack", targetKeywords.stream().limit(4).collect(Collectors.toList()));
        projects.add(project1);

        Map<String, Object> project2 = new LinkedHashMap<>();
        project2.put("name", "数据分析与指标看板项目");
        project2.put("goal", "体现业务理解、数据处理、结果呈现与汇报表达能力。");
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
            return "你的能力分布已经接近目标岗位要求，下一步不应只停留在继续学技能，而是要把已有能力沉淀成项目证据、量化成果和更强的简历表达。";
        }
        return "当前短板主要集中在 " + String.join("、", weakAreas)
                + "。建议先补齐这些高缺口技能，再同步准备 1 到 2 个可展示的项目案例，否则即使继续投递，岗位命中率和面试通过率也会受到明显影响。";
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
                ? "当前还没有形成稳定的高匹配岗位池，应该先补齐技能缺口，再进入集中投递阶段。"
                : "已经形成可投递岗位池，建议一边投递高分岗位，一边补齐最高优先级技能。");
        summary.put("topJobTitles", items.stream().map(item -> stringValue(item.get("title"))).limit(3).collect(Collectors.toList()));
        summary.put("prioritySkills", missingSkills.stream().limit(4).collect(Collectors.toList()));
        summary.put("nextStep", missingSkills.isEmpty()
                ? "立刻针对高分岗位刷新简历与项目表述，并开始分批投递。"
                : "先补齐优先技能，补一个能写进简历的证明项目，再重新生成推荐结果。");
        summary.put("executionAdvice", items.isEmpty()
                ? "最近 1 到 2 周先完成画像补全、技能补齐和简历改写，等匹配结果提升后再集中申请。"
                : "最近 1 周优先处理前 3 个高匹配岗位，同时把技能缺口拆成可执行学习任务，避免只有推荐没有转化。");
        return summary;
    }

    private Map<String, Object> buildSkillGapDiagnosis(
            SkillAdviceRequest req,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> marketSkills,
            double ratio
    ) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("targetRole", firstNonBlank(req.getTargetJobType(), inferTargetDirection(req.getUserSkills())));
        diagnosis.put("readinessLevel", ratio >= 70 ? "可投递" : ratio >= 45 ? "待补齐" : "需重建");
        diagnosis.put("matchedSkillCount", matchedSkills.size());
        diagnosis.put("missingSkillCount", missingSkills.size());
        diagnosis.put("marketCoreSkills", marketSkills.stream().limit(6).collect(Collectors.toList()));
        diagnosis.put("coreConclusion", ratio >= 70
                ? "你已经具备目标岗位的大部分核心技能，接下来重点在于项目证明与简历转化。"
                : ratio >= 45
                ? "你已经有一定基础，但还缺少几个决定投递结果的核心技能，需要先补关键短板。"
                : "你和目标岗位之间仍有明显能力断层，建议先把岗位要求拆成阶段性学习目标，再逐步进入投递。");
        diagnosis.put("priorityAction", missingSkills.isEmpty()
                ? "把已有技能包装成更强的项目案例和成果描述。"
                : "优先补齐 " + String.join("、", missingSkills.stream().limit(3).collect(Collectors.toList())) + "，并为每项技能准备可展示成果。");
        return diagnosis;
    }

    private Map<String, Object> buildCareerStrategySummary(CareerPathRequest req, List<Map<String, Object>> steps) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<String> targetSkills = steps.stream()
                .flatMap(step -> parseStringList(step.get("requiredSkills")).stream())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(6)
                .collect(Collectors.toList());
        summary.put("currentRole", req.getCurrentJob());
        summary.put("targetRole", req.getTargetJob());
        summary.put("transitionStageCount", steps.size());
        summary.put("recommendedFocus", targetSkills);
        summary.put("strategy", steps.size() <= 2
                ? "你的转型路径相对清晰，适合采用“补齐技能 + 做证明项目 + 定向投递”的短周期策略。"
                : "你的路径跨度较大，更适合分阶段完成技能升级、项目积累和岗位过渡，不建议一次性跨得过深。");
        summary.put("executionHint", "每个阶段至少准备 1 个可被招聘方识别的成果物，如作品项目、实习经历、业务案例或量化成绩。");
        return summary;
    }

    private Map<String, Object> buildResumeDiagnosis(
            int totalScore,
            List<String> suggestions,
            List<String> missingSkills,
            List<String> targetKeywords
    ) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("readinessLevel", totalScore >= 78 ? "可直接投递" : totalScore >= 60 ? "优化后投递" : "需重点重写");
        diagnosis.put("keywordCoverage", targetKeywords.stream().limit(6).collect(Collectors.toList()));
        diagnosis.put("gapSkills", missingSkills.stream().limit(5).collect(Collectors.toList()));
        diagnosis.put("coreIssue", totalScore >= 78
                ? "简历主体结构已经具备投递基础，当前更需要增强结果呈现与岗位针对性。"
                : totalScore >= 60
                ? "简历存在可用基础，但在项目证据、量化结果或关键词覆盖上仍影响转化。"
                : "简历与目标岗位的贴合度偏低，需要从结构、内容和关键词三个层面重新组织。");
        diagnosis.put("priorityRevision", suggestions.stream().limit(3).collect(Collectors.toList()));
        return diagnosis;
    }
}
