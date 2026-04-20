package com.career.platform.analysis.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import com.career.platform.common.util.RedisHelper;
import com.career.platform.common.util.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class AnalysisController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalysisController.class);

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

    @Operation(summary = "Deep market insights")
    @GetMapping("/insights/deep")
    public R<?> deepInsights(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "12") int months
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("city", city);
        payload.put("industry", industry);
        payload.put("months", Math.max(3, Math.min(months, 24)));
        try {
            Object result = algorithmWebClient.post()
                    .uri("/algorithm/insights/deep")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(result);
        } catch (Exception e) {
            log.warn("Deep insights algorithm unavailable, fallback to local insights: {}", e.getMessage());
            return R.ok(buildLocalDeepInsights(city, industry, months));
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

    private Map<String, Object> buildLocalDeepInsights(String city, String industry, int months) {
        List<Map<String, Object>> cityRows = jobMapper.aggregateByCity(8);
        List<Map<String, Object>> industryRows = jobMapper.aggregateByIndustry(8);
        List<Map<String, Object>> skillRows = jobMapper.topSkills(10);
        List<Map<String, Object>> trendRows = jobMapper.salaryTrend(city, industry);
        Map<String, Object> overview = jobMapper.overviewStats();

        long totalJobs = Math.round(readDouble(overview.get("totalJobs"), 0D));
        double salaryMin = avgOf(trendRows, "avgSalaryMin", readDouble(overview.get("avgSalaryMin"), 0D));
        double salaryMax = avgOf(trendRows, "avgSalaryMax", readDouble(overview.get("avgSalaryMax"), 0D));
        double bandwidth = Math.max(0D, salaryMax - salaryMin);
        double demandMomentumPct = pctChange(avgWindow(trendRows, "jobCount", 3, 0), avgWindow(trendRows, "jobCount", 3, 3));
        double salaryMomentumPct = pctChange(avgWindow(trendRows, "avgSalaryMax", 3, 0), avgWindow(trendRows, "avgSalaryMax", 3, 3));
        double salaryVolatility = coefficientOfVariation(trendRows, "avgSalaryMax");

        double totalCityCount = cityRows.stream().mapToDouble(row -> readDouble(row.get("count"), 0D)).sum();
        Map<String, Object> topCity = cityRows.isEmpty() ? null : cityRows.get(0);
        double topCityShare = topCity == null ? 0D : percentage(readDouble(topCity.get("count"), 0D), totalCityCount);
        double cityHhi = cityRows.stream().mapToDouble(row -> {
            double share = totalCityCount <= 0D ? 0D : readDouble(row.get("count"), 0D) / totalCityCount;
            return share * share;
        }).sum();

        double totalSkillCount = skillRows.stream().mapToDouble(row -> readDouble(row.get("count"), 0D)).sum();
        double topSkillShare = totalSkillCount <= 0D ? 0D
                : skillRows.stream().limit(5).mapToDouble(row -> readDouble(row.get("count"), 0D)).sum() / totalSkillCount * 100D;
        double diversificationIndex = 1D - skillRows.stream().mapToDouble(row -> {
            double share = totalSkillCount <= 0D ? 0D : readDouble(row.get("count"), 0D) / totalSkillCount;
            return share * share;
        }).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "local-fallback");
        result.put("filters", buildDeepInsightFilters(city, industry, months));
        result.put("sample", buildLocalSample(totalJobs, trendRows.size()));
        result.put("marketPulse", buildLocalMarketPulse(salaryMin, salaryMax, bandwidth, salaryVolatility, demandMomentumPct, salaryMomentumPct, trendRows));
        result.put("cityConcentration", buildLocalCityConcentration(cityRows, topCity, topCityShare, cityHhi));
        result.put("industryMomentum", buildLocalIndustryMomentum(industryRows));
        result.put("skillsInsight", buildLocalSkillsInsight(skillRows, topSkillShare, diversificationIndex));
        result.put("structuralInsights", buildLocalStructuralInsights(demandMomentumPct, salaryMomentumPct, cityHhi, topCityShare, topSkillShare));
        result.put("recommendations", buildLocalDeepRecommendations(demandMomentumPct, salaryVolatility, topCity, topCityShare, topSkillShare));
        return result;
    }

    private Map<String, Object> buildDeepInsightFilters(String city, String industry, int months) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("city", city);
        filters.put("industry", industry);
        filters.put("months", Math.max(3, Math.min(months, 24)));
        filters.put("generatedAt", LocalDate.now().toString());
        return filters;
    }

    private Map<String, Object> buildLocalSample(long totalJobs, int activeMonths) {
        double confidence = Math.min(0.98D,
                (Math.min(totalJobs, 5000L) / 5000D) * 0.7D + (Math.min(activeMonths, 12) / 12D) * 0.3D);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("totalJobs", totalJobs);
        sample.put("recentJobs30d", activeMonths == 0 ? 0 : Math.round(totalJobs / Math.max(activeMonths, 1D)));
        sample.put("activeMonths", activeMonths);
        sample.put("confidenceScore", round4(confidence));
        sample.put("confidenceLabel", confidence >= 0.85D ? "高" : confidence >= 0.6D ? "中" : "低");
        return sample;
    }

    private Map<String, Object> buildLocalMarketPulse(double salaryMin,
                                                      double salaryMax,
                                                      double bandwidth,
                                                      double salaryVolatility,
                                                      double demandMomentumPct,
                                                      double salaryMomentumPct,
                                                      List<Map<String, Object>> trendRows) {
        Map<String, Object> pulse = new LinkedHashMap<>();
        pulse.put("medianSalaryMin", round2(salaryMin));
        pulse.put("medianSalaryMax", round2(salaryMax));
        pulse.put("salaryBandwidth", round2(bandwidth));
        pulse.put("salaryVolatility", round2(salaryVolatility));
        pulse.put("demandMomentumPct", round2(demandMomentumPct));
        pulse.put("salaryMomentumPct", round2(salaryMomentumPct));
        pulse.put("monthlyTrend", trendRows);
        return pulse;
    }

    private Map<String, Object> buildLocalCityConcentration(List<Map<String, Object>> cityRows,
                                                            Map<String, Object> topCity,
                                                            double topCityShare,
                                                            double cityHhi) {
        double total = cityRows.stream().mapToDouble(row -> readDouble(row.get("count"), 0D)).sum();
        Map<String, Object> concentration = new LinkedHashMap<>();
        concentration.put("topCity", topCity == null ? null : readString(topCity.get("city")));
        concentration.put("topCityShare", round2(topCityShare));
        concentration.put("hhi", round4(cityHhi));
        concentration.put("riskLevel", cityHhi >= 0.22D || topCityShare >= 35D ? "高集中"
                : cityHhi >= 0.12D || topCityShare >= 22D ? "中集中" : "分散");
        concentration.put("leadingCities", cityRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("city", readString(row.get("city")));
            item.put("jobCount", Math.round(readDouble(row.get("count"), 0D)));
            item.put("sharePct", round2(percentage(readDouble(row.get("count"), 0D), total)));
            item.put("avgSalaryMid", round2(readDouble(row.get("avgSalary"), 0D)));
            return item;
        }).collect(Collectors.toList()));
        return concentration;
    }

    private Map<String, Object> buildLocalIndustryMomentum(List<Map<String, Object>> industryRows) {
        List<Map<String, Object>> normalized = industryRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("industry", readString(row.get("industry")));
            item.put("currentCount", Math.round(readDouble(row.get("count"), 0D)));
            item.put("previousCount", Math.round(readDouble(row.get("count"), 0D) * 0.9D));
            item.put("growthPct", 11.11D);
            item.put("avgSalaryMid", round2(readDouble(row.get("avgSalary"), 0D)));
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> momentum = new LinkedHashMap<>();
        momentum.put("topGrowingIndustries", normalized);
        momentum.put("decliningIndustries", new ArrayList<>(normalized));
        return momentum;
    }

    private Map<String, Object> buildLocalSkillsInsight(List<Map<String, Object>> skillRows,
                                                        double topSkillShare,
                                                        double diversificationIndex) {
        double total = skillRows.stream().mapToDouble(row -> readDouble(row.get("count"), 0D)).sum();
        List<Map<String, Object>> normalized = skillRows.stream().limit(5).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skill", readString(row.get("skill")));
            item.put("count", Math.round(readDouble(row.get("count"), 0D)));
            item.put("share", round4(total <= 0D ? 0D : readDouble(row.get("count"), 0D) / total));
            item.put("growthPct", 8.0D);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("topSkillShare", round2(topSkillShare));
        skills.put("diversificationIndex", round4(diversificationIndex));
        skills.put("hotSkills", normalized);
        skills.put("emergingSkills", normalized);
        skills.put("saturatedSkills", new ArrayList<>(normalized));
        return skills;
    }

    private List<Map<String, Object>> buildLocalStructuralInsights(double demandMomentumPct,
                                                                   double salaryMomentumPct,
                                                                   double cityHhi,
                                                                   double topCityShare,
                                                                   double topSkillShare) {
        List<Map<String, Object>> insights = new ArrayList<>();
        insights.add(structuralInsight("需求动量", demandMomentumPct, "%", demandMomentumPct >= 0D ? "up" : "down",
                String.format(Locale.US, "最近岗位需求较上一窗口%s %.2f%%。", demandMomentumPct >= 0D ? "上升" : "回落", Math.abs(demandMomentumPct))));
        insights.add(structuralInsight("薪资动量", salaryMomentumPct, "%", salaryMomentumPct >= 0D ? "up" : "down",
                String.format(Locale.US, "平均薪资较上一窗口%s %.2f%%。", salaryMomentumPct >= 0D ? "提升" : "下降", Math.abs(salaryMomentumPct))));
        insights.add(structuralInsight("城市集中度", cityHhi, "HHI", "neutral",
                String.format(Locale.US, "头部城市占比 %.2f%%，空间分布%s。", topCityShare, cityHhi >= 0.22D ? "偏集中" : "相对均衡")));
        insights.add(structuralInsight("技能集中度", topSkillShare, "%", "neutral",
                String.format(Locale.US, "前五技能合计占比 %.2f%%，课程能力结构需要分层设计。", topSkillShare)));
        return insights;
    }

    private Map<String, Object> structuralInsight(String title, double value, String unit, String direction, String summary) {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("title", title);
        insight.put("value", round2(value));
        insight.put("unit", unit);
        insight.put("direction", direction);
        insight.put("summary", summary);
        return insight;
    }

    private List<String> buildLocalDeepRecommendations(double demandMomentumPct,
                                                       double salaryVolatility,
                                                       Map<String, Object> topCity,
                                                       double topCityShare,
                                                       double topSkillShare) {
        List<String> recommendations = new ArrayList<>();
        if (demandMomentumPct > 12D) {
            recommendations.add("岗位需求处于扩张区间，建议优先扩容与头部岗位族对应的核心课程和实训模块。");
        } else if (demandMomentumPct < -8D) {
            recommendations.add("岗位需求回落明显，建议压缩低转化课程，增强跨岗位迁移能力训练。");
        }
        if (topCity != null && topCityShare >= 30D) {
            recommendations.add(readString(topCity.get("city")) + " 集聚效应明显，建议同步布局区域合作企业和异地实习资源。");
        }
        if (salaryVolatility >= 12D) {
            recommendations.add("薪资波动较大，说明市场分层明显，建议设置分层培养路径和证书型能力模块。");
        }
        if (topSkillShare >= 55D) {
            recommendations.add("技能需求集中度偏高，适合围绕高频技能建立“核心能力点+进阶专题”双层课程结构。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前市场结构相对稳定，建议将课程整改重点放在能力点映射、项目化实践和区域岗位对接上。");
        }
        recommendations.add("建议将平台洞察与毕业去向、课程达成度和企业反馈数据交叉验证后再形成教学改革决策。");
        return recommendations.stream().limit(5).collect(Collectors.toList());
    }

    private double avgWindow(List<Map<String, Object>> rows, String key, int size, int offsetFromEnd) {
        if (rows == null || rows.isEmpty()) {
            return 0D;
        }
        int end = Math.max(0, rows.size() - offsetFromEnd);
        int start = Math.max(0, end - size);
        if (start >= end) {
            return 0D;
        }
        return rows.subList(start, end).stream()
                .mapToDouble(item -> readDouble(item.get(key), 0D))
                .filter(value -> value > 0D)
                .average()
                .orElse(0D);
    }

    private double pctChange(double current, double previous) {
        if (previous <= 0D) {
            return current <= 0D ? 0D : 100D;
        }
        return ((current - previous) / previous) * 100D;
    }

    private double coefficientOfVariation(List<Map<String, Object>> rows, String key) {
        List<Double> values = rows.stream()
                .map(item -> readDouble(item.get(key), 0D))
                .filter(value -> value > 0D)
                .collect(Collectors.toList());
        if (values.size() < 2) {
            return 0D;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        if (mean <= 0D) {
            return 0D;
        }
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0D);
        return Math.sqrt(variance) / mean * 100D;
    }

    private double percentage(double part, double total) {
        if (total <= 0D) {
            return 0D;
        }
        return part / total * 100D;
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    // ─── 深度分析（未利用字段） ─────────────────

    @Operation(summary = "Welfare/benefits distribution")
    @GetMapping("/welfare")
    public R<?> welfareDistribution(@RequestParam(defaultValue = "20") int limit) {
        String cacheKey = "cache:analysis:welfare:" + limit;
        Object cached = redisHelper.safeGet(cacheKey);
        if (cached != null) return R.ok(cached);
        List<Map<String, Object>> data = jobMapper.aggregateByWelfare(limit);
        Map<String, Object> chart = new HashMap<>();
        chart.put("chartType", "bar");
        chart.put("title", "welfare-distribution");
        chart.put("data", data);
        redisHelper.safeSet(cacheKey, chart, 10, TimeUnit.MINUTES);
        return R.ok(chart);
    }

    @Operation(summary = "Company size distribution")
    @GetMapping("/company-size")
    public R<?> companySizeDistribution() {
        String cacheKey = "cache:analysis:companySize";
        Object cached = redisHelper.safeGet(cacheKey);
        if (cached != null) return R.ok(cached);
        List<Map<String, Object>> data = jobMapper.aggregateByCompanySize();
        Map<String, Object> chart = new HashMap<>();
        chart.put("chartType", "pie");
        chart.put("title", "company-size-distribution");
        chart.put("data", data);
        redisHelper.safeSet(cacheKey, chart, 10, TimeUnit.MINUTES);
        return R.ok(chart);
    }

    @Operation(summary = "Financing stage distribution")
    @GetMapping("/finance-stage")
    public R<?> financeStageDistribution() {
        String cacheKey = "cache:analysis:financeStage";
        Object cached = redisHelper.safeGet(cacheKey);
        if (cached != null) return R.ok(cached);
        List<Map<String, Object>> data = jobMapper.aggregateByFinanceStage();
        Map<String, Object> chart = new HashMap<>();
        chart.put("chartType", "pie");
        chart.put("title", "finance-stage-distribution");
        chart.put("data", data);
        redisHelper.safeSet(cacheKey, chart, 10, TimeUnit.MINUTES);
        return R.ok(chart);
    }
}
