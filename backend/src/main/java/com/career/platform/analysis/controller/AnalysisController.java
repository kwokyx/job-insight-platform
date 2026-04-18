package com.career.platform.analysis.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import com.career.platform.common.util.RedisHelper;
import com.career.platform.common.util.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Tag(name = "Data Analysis", description = "Overview, salary, skills, region, and algorithm proxy APIs")
@RestController
@RequestMapping("/api/v1/analysis")
@Slf4j
public class AnalysisController {

    private final JobPostingMapper jobMapper;
    private final RedisHelper redisHelper;
    private final WebClient algorithmWebClient;
    private final UserInsightService userInsightService;
    private final MarketSkillService marketSkillService;
    private final Executor dbQueryExecutor;

    public AnalysisController(JobPostingMapper jobMapper,
                              RedisHelper redisHelper,
                              @Qualifier("algorithmWebClient") WebClient algorithmWebClient,
                              UserInsightService userInsightService,
                              MarketSkillService marketSkillService,
                              @Qualifier("dbQueryExecutor") Executor dbQueryExecutor) {
        this.jobMapper = jobMapper;
        this.redisHelper = redisHelper;
        this.algorithmWebClient = algorithmWebClient;
        this.userInsightService = userInsightService;
        this.marketSkillService = marketSkillService;
        this.dbQueryExecutor = dbQueryExecutor;
    }

    @Operation(summary = "Overview dashboard")
    @GetMapping("/overview")
    @SuppressWarnings("unchecked")
    public R<?> overview() {
        String cacheKey = "cache:analysis:overview";
        Object cached = redisHelper.safeGet(cacheKey);
        if (cached != null) {
            return R.ok(cached);
        }

        // 并行执行 8 个独立查询，总耗时 ≈ max(各查询耗时) 而非 Σ
        CompletableFuture<Map<String, Object>> statsFuture =
                CompletableFuture.supplyAsync(jobMapper::overviewStats, dbQueryExecutor);
        CompletableFuture<Long> countFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.selectCount(null), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> citiesFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.aggregateByCity(10), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> industriesFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.aggregateByIndustry(10), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> skillsFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.topSkills(10), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> educationFuture =
                CompletableFuture.supplyAsync(jobMapper::aggregateByEducation, dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> expFuture =
                CompletableFuture.supplyAsync(jobMapper::aggregateByExperience, dbQueryExecutor);

        CompletableFuture.allOf(statsFuture, countFuture, citiesFuture, industriesFuture,
                skillsFuture, educationFuture, expFuture).join();

        Map<String, Object> stats = statsFuture.join();
        Map<String, Object> data = new HashMap<>();
        data.put("totalJobs", countFuture.join());
        data.put("avgSalaryMin", stats.get("avgSalaryMin"));
        data.put("avgSalaryMax", stats.get("avgSalaryMax"));
        data.put("topCities", citiesFuture.join());
        data.put("topIndustries", industriesFuture.join());
        data.put("topSkills", skillsFuture.join());
        data.put("educationDistribution", educationFuture.join());
        data.put("experienceDistribution", expFuture.join());

        redisHelper.safeSet(cacheKey, data, 10, TimeUnit.MINUTES);
        return R.ok(data);
    }

    @Operation(summary = "Personalized overview")
    @GetMapping("/overview/personalized")
    public R<?> personalizedOverview() {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) {
            return R.unauthorized("Please login first");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> userContext = userInsightService.loadUserContext(userId);
        String city = readString(userContext.get("targetCityCode"));
        String industry = readString(userContext.get("profileSummary"));

        Map<String, Object> result = new HashMap<>();
        result.put("advisory", userInsightService.buildPlatformAdvisory(userId));
        result.put("salaryTrend", jobMapper.salaryTrend(city, industry));
        result.put("topSkills", jobMapper.topSkills(10));
        result.put("filters", buildFilters(city, industry));
        return R.ok(result);
    }

    @Operation(summary = "Salary analysis")
    @GetMapping("/salary")
    public R<?> salaryAnalysis(
            @RequestParam(defaultValue = "city") String groupBy,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<Map<String, Object>> data;
        switch (groupBy) {
            case "industry":
                data = jobMapper.aggregateByIndustry(limit);
                break;
            case "education":
                data = jobMapper.aggregateByEducation();
                break;
            case "experience":
                data = jobMapper.aggregateByExperience();
                break;
            default:
                data = jobMapper.aggregateByCity(limit);
                break;
        }

        Map<String, Object> chart = new HashMap<>();
        chart.put("chartType", "bar");
        chart.put("title", "salary-analysis");
        chart.put("groupBy", groupBy);
        chart.put("data", data);
        return R.ok(chart);
    }

    @Operation(summary = "Salary trend")
    @GetMapping("/salary/trend")
    public R<?> salaryTrend(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry
    ) {
        String cacheKey = "cache:analysis:salaryTrend:"
                + (city == null ? "" : city.trim()) + ":"
                + (industry == null ? "" : industry.trim());
        Object cached = redisHelper.safeGet(cacheKey);
        if (cached != null) {
            return R.ok(cached);
        }

        List<Map<String, Object>> rows = jobMapper.salaryTrend(city, industry);
        Map<String, Object> filters = new HashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);

        Map<String, Object> seriesMin = new HashMap<>();
        seriesMin.put("name", "avgSalaryMin");
        seriesMin.put("data", rows.stream().map(row -> row.get("avgSalaryMin")).collect(Collectors.toList()));

        Map<String, Object> seriesMax = new HashMap<>();
        seriesMax.put("name", "avgSalaryMax");
        seriesMax.put("data", rows.stream().map(row -> row.get("avgSalaryMax")).collect(Collectors.toList()));

        Map<String, Object> seriesCount = new HashMap<>();
        seriesCount.put("name", "jobCount");
        seriesCount.put("data", rows.stream().map(row -> row.get("jobCount")).collect(Collectors.toList()));

        Map<String, Object> chart = new HashMap<>();
        chart.put("chartType", "line");
        chart.put("xAxis", rows.stream().map(row -> row.get("period")).collect(Collectors.toList()));
        chart.put("series", Arrays.asList(seriesMin, seriesMax, seriesCount));
        chart.put("filters", filters);
        chart.put("data", rows);
        redisHelper.safeSet(cacheKey, chart, 10, TimeUnit.MINUTES);
        return R.ok(chart);
    }

    @Operation(summary = "Skills ranking")
    @GetMapping("/skills")
    public R<?> skillsRanking(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(jobMapper.topSkills(limit));
    }

    @Operation(summary = "Skill graph")
    @GetMapping("/skills/graph")
    public R<?> skillGraph(@RequestParam(defaultValue = "50") int topN) {
        try {
            Object result = algorithmWebClient.post()
                    .uri("/algorithm/skills/graph?top_n=" + topN)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            log.warn("Skill graph algorithm unavailable, fallback to local graph: {}", e.getMessage());
            return R.ok(buildLocalSkillGraph(topN));
        }
    }

    @Log("薪资预测")
    @Operation(summary = "Salary prediction")
    @PostMapping("/salary/predict")
    public R<?> salaryPredict(@RequestBody Map<String, Object> params) {
        try {
            Object result = algorithmWebClient.post()
                    .uri("/algorithm/salary/predict")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            log.warn("Salary prediction algorithm unavailable, fallback to local estimator: {}", e.getMessage());
            return R.ok(buildLocalSalaryPrediction(params));
        }
    }

    @Operation(summary = "Region heatmap")
    @GetMapping("/regions/heatmap")
    public R<?> regionHeatmap() {
        List<Map<String, Object>> data = jobMapper.aggregateByCity(50);
        Map<String, Object> chart = new HashMap<>();
        chart.put("chartType", "map");
        chart.put("title", "job-region-heatmap");
        chart.put("data", data);
        return R.ok(chart);
    }

    @Operation(summary = "Sentiment index")
    @GetMapping("/sentiment")
    public R<?> sentiment(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry
    ) {
        try {
            StringBuilder uri = new StringBuilder("/algorithm/sentiment/index?");
            if (city != null) {
                uri.append("city=").append(city).append("&");
            }
            if (industry != null) {
                uri.append("industry=").append(industry);
            }

            Object result = algorithmWebClient.post()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            log.warn("Sentiment algorithm unavailable, fallback to local sentiment: {}", e.getMessage());
            return R.ok(buildLocalSentiment(city, industry));
        }
    }

    private Map<String, Object> buildLocalSkillGraph(int topN) {
        List<Map<String, Object>> topSkills = marketSkillService.topSkills(Math.min(Math.max(topN, 10), 40));
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (int i = 0; i < topSkills.size(); i++) {
            Map<String, Object> skill = topSkills.get(i);
            String name = readString(skill.get("skill"));
            if (name == null) {
                continue;
            }
            Map<String, Object> node = new HashMap<>();
            node.put("id", name);
            node.put("label", name);
            node.put("value", readDouble(skill.get("count"), 1D));
            nodes.add(node);

            if (i > 0) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("source", readString(topSkills.get(0).get("skill")));
                edge.put("target", name);
                edge.put("weight", Math.max(1, topSkills.size() - i));
                edges.add(edge);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("source", "local-fallback");
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("topN", topN);
        return result;
    }

    private Map<String, Object> buildLocalSentiment(String city, String industry) {
        List<Map<String, Object>> trendRows = jobMapper.salaryTrend(city, industry);
        Map<String, Object> overview = jobMapper.overviewStats();
        double avgMin = avgOf(trendRows, "avgSalaryMin", readDouble(overview.get("avgSalaryMin"), 4000D));
        double avgMax = avgOf(trendRows, "avgSalaryMax", readDouble(overview.get("avgSalaryMax"), 6500D));
        double jobCount = trendRows.stream().mapToDouble(row -> readDouble(row.get("jobCount"), 0D)).sum();

        int score = 50;
        if (avgMin >= 5000) score += 10;
        if (avgMax >= 8000) score += 10;
        if (jobCount >= 1000) score += 10;
        if (trendRows.size() >= 6) score += 5;
        score = Math.min(score, 95);

        Map<String, Object> result = new HashMap<>();
        result.put("source", "local-fallback");
        result.put("city", city);
        result.put("industry", industry);
        result.put("score", score);
        result.put("label", score >= 75 ? "positive" : score >= 55 ? "neutral" : "cautious");
        result.put("summary", "基于岗位样本量和薪资区间估算当前市场情绪。");
        result.put("signals", buildPredictionFactors(city, industry, null, null, null));
        return result;
    }


    private String readString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> buildFilters(String city, String industry) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);
        return filters;
    }

    private Map<String, Object> buildLocalSalaryPrediction(Map<String, Object> params) {
        String city = readString(params.get("city"));
        String industry = readString(params.get("industry"));
        String education = readString(params.get("education"));
        String experience = readString(params.get("experience"));

        List<Map<String, Object>> trendRows = jobMapper.salaryTrend(city, industry);
        Map<String, Object> overview = jobMapper.overviewStats();

        double baseMin = avgOf(trendRows, "avgSalaryMin", readDouble(overview.get("avgSalaryMin"), 4000D));
        double baseMax = avgOf(trendRows, "avgSalaryMax", readDouble(overview.get("avgSalaryMax"), 6500D));

        double factor = educationFactor(education) * experienceFactor(experience) * skillFactor(params.get("skills"));
        double predictedMin = round2(baseMin * factor);
        double predictedMax = round2(Math.max(baseMax * factor, predictedMin));

        Map<String, Object> result = new HashMap<>();
        result.put("predictedSalaryMin", predictedMin);
        result.put("predictedSalaryMax", predictedMax);
        result.put("salaryText", String.format(Locale.US, "%.2f - %.2f", predictedMin, predictedMax));
        result.put("source", "local-fallback");
        result.put("confidence", trendRows.isEmpty() ? "medium" : "high");
        result.put("sampleCount", trendRows.size());
        result.put("factors", buildPredictionFactors(city, industry, education, experience, params.get("skills")));
        return result;
    }

    private Map<String, Object> buildPredictionFactors(String city, String industry, String education, String experience, Object skillsRaw) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("city", city);
        factors.put("industry", industry);
        factors.put("education", education);
        factors.put("experience", experience);
        factors.put("skills", parseSkills(skillsRaw));
        return factors;
    }

    private List<String> parseSkills(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                String text = readString(item);
                if (text != null) {
                    result.add(text);
                }
            }
            return result;
        }
        String text = readString(raw);
        if (text == null) {
            return result;
        }
        for (String item : text.split("[,，、/|]+")) {
            String skill = item.trim();
            if (!skill.isEmpty()) {
                result.add(skill);
            }
        }
        return result;
    }

    private double skillFactor(Object skillsRaw) {
        int count = parseSkills(skillsRaw).size();
        if (count >= 6) return 1.12D;
        if (count >= 4) return 1.07D;
        if (count >= 2) return 1.03D;
        return 1.0D;
    }

    private double educationFactor(String education) {
        String text = education == null ? "" : education.toLowerCase(Locale.ROOT);
        if (text.contains("博士") || text.contains("phd")) return 1.20D;
        if (text.contains("硕士") || text.contains("master")) return 1.12D;
        if (text.contains("本科") || text.contains("bachelor")) return 1.05D;
        if (text.contains("大专") || text.contains("college")) return 0.98D;
        return 1.0D;
    }

    private double experienceFactor(String experience) {
        String text = experience == null ? "" : experience.toLowerCase(Locale.ROOT);
        if (text.contains("5") || text.contains("senior")) return 1.22D;
        if (text.contains("3")) return 1.12D;
        if (text.contains("1")) return 1.04D;
        if (text.contains("应届") || text.contains("0")) return 0.92D;
        return 1.0D;
    }

    private double avgOf(List<Map<String, Object>> rows, String key, double fallback) {
        if (rows == null || rows.isEmpty()) {
            return fallback;
        }
        return rows.stream()
                .mapToDouble(item -> readDouble(item.get(key), 0D))
                .filter(value -> value > 0D)
                .average()
                .orElse(fallback);
    }

    private double readDouble(Object value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
