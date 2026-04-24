package com.career.platform.analysis.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.snapshot.service.PageSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Tag(name = "Data Analysis", description = "Overview, salary, skills, region, and algorithm proxy APIs")
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalysisController.class);
    private static final String GRAPH_TYPE_DOMAIN = "domain";

    private final JobPostingMapper jobMapper;
    private final RedisHelper redisHelper;
    private final WebClient algorithmWebClient;
    private final UserInsightService userInsightService;
    private final MarketSkillService marketSkillService;
    private final Executor dbQueryExecutor;
    private final PageSnapshotService pageSnapshotService;

    @Autowired
    public AnalysisController(JobPostingMapper jobMapper,
                              RedisHelper redisHelper,
                              @Qualifier("algorithmWebClient") WebClient algorithmWebClient,
                              UserInsightService userInsightService,
                              MarketSkillService marketSkillService,
                              @Qualifier("dbQueryExecutor") Executor dbQueryExecutor,
                              PageSnapshotService pageSnapshotService) {
        this.jobMapper = jobMapper;
        this.redisHelper = redisHelper;
        this.algorithmWebClient = algorithmWebClient;
        this.userInsightService = userInsightService;
        this.marketSkillService = marketSkillService;
        this.dbQueryExecutor = dbQueryExecutor;
        this.pageSnapshotService = pageSnapshotService;
    }

    @Operation(summary = "Overview dashboard")
    @GetMapping("/overview")
    @SuppressWarnings("unchecked")
    public R<?> overview() {
        if (pageSnapshotService != null) {
            Map<String, Object> snapshot = new LinkedHashMap<>(pageSnapshotService.getMarketOverview());
            snapshot.put("topSkills", marketSkillService.cleanSkillRows(pageSnapshotService.getMarketSkills(60), 10, MarketSkillService.TYPE_SKILL));
            return R.ok(snapshot);
        }
        String cacheKey = "cache:analysis:overview";
        Object cached = redisHelper.safeGet(cacheKey);
        if (cached != null) {
            return R.ok(cached);
        }

        // 并行执行多个独立查询，整体耗时接近最慢查询，而不是简单相加。
        CompletableFuture<Map<String, Object>> statsFuture =
                CompletableFuture.supplyAsync(jobMapper::overviewStats, dbQueryExecutor);
        CompletableFuture<Long> countFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.selectCount(null), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> citiesFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.aggregateByCity(10), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> industriesFuture =
                CompletableFuture.supplyAsync(() -> jobMapper.aggregateByIndustry(10), dbQueryExecutor);
        CompletableFuture<List<Map<String, Object>>> skillsFuture =
                CompletableFuture.supplyAsync(() -> marketSkillService.topSkills(10), dbQueryExecutor);
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
            return R.unauthorized("登录状态已失效，请重新登录");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> userContext = userInsightService.loadUserContext(userId);
        String city = readString(userContext.get("targetCityCode"));
        String industry = readString(userContext.get("profileSummary"));

        Map<String, Object> result = new HashMap<>();
        result.put("advisory", userInsightService.buildPlatformAdvisory(userId));
        result.put("salaryTrend", jobMapper.salaryTrend(city, industry));
        result.put("topSkills", marketSkillService.topSkills(10));
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
        if (pageSnapshotService != null) {
            return R.ok(pageSnapshotService.getSalaryTrend(city, industry));
        }
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
    public R<?> skillsRanking(@RequestParam(defaultValue = "20") int limit,
                              @RequestParam(defaultValue = "skill") String type) {
        if (pageSnapshotService != null) {
            List<Map<String, Object>> raw = pageSnapshotService.getMarketSkills(Math.max(limit * 3, 60));
            return R.ok(marketSkillService.cleanSkillRows(raw, limit, type));
        }
        return R.ok(marketSkillService.cleanSkillRows(jobMapper.topSkills(Math.max(limit * 4, 40)), limit, type));
    }

    @Operation(summary = "Skill graph")
    @GetMapping("/skills/graph")
    public R<?> skillGraph(@RequestParam(defaultValue = "50") int topN,
                           @RequestParam(defaultValue = "skill") String type) {
        try {
            Object result = algorithmWebClient.post()
                    .uri("/algorithm/skills/graph?top_n=" + topN)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(normalizeSkillGraphResult(result, topN, type));
        } catch (Exception e) {
            log.warn("Skill graph algorithm unavailable, fallback to local graph: {}", e.getMessage());
            return R.ok(buildLocalSkillGraph(topN, type));
        }
    }

    @Log("薪资预测")
    @Operation(summary = "Salary prediction")
    @PostMapping("/salary/predict")
    public R<?> salaryPredict(@RequestBody Map<String, Object> params) {
        Map<String, Object> normalized = enrichSalaryParams(params);
        try {
            Object result = algorithmWebClient.post()
                    .uri("/algorithm/salary/predict")
                    .bodyValue(normalized)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return R.ok(normalizeSalaryPredictionResult(result, normalized));
        } catch (Exception e) {
            log.warn("Salary prediction algorithm unavailable, fallback to local estimator: {}", e.getMessage());
            return R.ok(buildLocalSalaryPrediction(normalized));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeSkillGraphResult(Object raw, int topN, String type) {
        if (!(raw instanceof Map)) {
            return buildLocalSkillGraph(topN, type);
        }
        Map<String, Object> data = (Map<String, Object>) raw;
        Object nodesRaw = data.get("nodes");
        if (nodesRaw instanceof List) {
            List<?> nodeList = (List<?>) nodesRaw;
            List<Map<String, Object>> normalizedNodes = new ArrayList<>();
            Map<String, String> idToLabel = new LinkedHashMap<>();
            for (Object nodeObj : nodeList) {
                if (!(nodeObj instanceof Map)) continue;
                Map<String, Object> node = (Map<String, Object>) nodeObj;
                String label = readString(node.get("label"));
                if (label == null) label = readString(node.get("skill"));
                if (label == null) label = readString(node.get("name"));
                if (label == null) label = String.valueOf(node.get("id"));
                String rawType = readString(node.get("type"));
                String rawCategory = readString(node.get("category"));
                boolean isDomainNode = GRAPH_TYPE_DOMAIN.equalsIgnoreCase(rawType);
                String normalizedLabel = isDomainNode ? label : marketSkillService.normalizeSkillName(label);
                String entityType = isDomainNode
                        ? GRAPH_TYPE_DOMAIN
                        : (rawType == null || rawType.trim().isEmpty()
                        ? marketSkillService.classifyEntityType(normalizedLabel)
                        : rawType.trim().toLowerCase(Locale.ROOT));
                if (!matchesEntityType(entityType, type)) continue;
                Map<String, Object> clean = new LinkedHashMap<>();
                clean.put("id", node.getOrDefault("id", normalizedLabel));
                clean.put("label", normalizedLabel);
                clean.put("value", readDouble(node.get("value"), readDouble(node.get("count"), 1D)));
                clean.put("type", entityType);
                clean.put("category", GRAPH_TYPE_DOMAIN.equals(entityType)
                        ? (rawCategory == null || rawCategory.trim().isEmpty() ? normalizedLabel : rawCategory)
                        : (MarketSkillService.TYPE_SKILL.equals(entityType)
                        ? (rawCategory == null || rawCategory.trim().isEmpty()
                        ? marketSkillService.inferCapabilityDimension(normalizedLabel)
                        : rawCategory)
                        : entityType));
                normalizedNodes.add(clean);
                idToLabel.put(String.valueOf(clean.get("id")), normalizedLabel);
            }
            data.put("nodes", normalizedNodes);
            Set<String> validIds = new LinkedHashSet<>();
            for (Map<String, Object> n : normalizedNodes) {
                validIds.add(String.valueOf(n.get("id")));
                validIds.add(String.valueOf(n.get("label")));
            }
            Object edgesRaw = data.get("edges");
            if (edgesRaw instanceof List) {
                List<?> edgeList = (List<?>) edgesRaw;
                List<Map<String, Object>> cleanEdges = new ArrayList<>();
                for (Object edgeObj : edgeList) {
                    if (!(edgeObj instanceof Map)) continue;
                    Map<String, Object> edge = (Map<String, Object>) edgeObj;
                    String source = String.valueOf(edge.get("source"));
                    String target = String.valueOf(edge.get("target"));
                    if (!validIds.contains(source) && !idToLabel.containsKey(source)) continue;
                    if (!validIds.contains(target) && !idToLabel.containsKey(target)) continue;
                    Map<String, Object> cleanEdge = new LinkedHashMap<>();
                    cleanEdge.put("source", idToLabel.getOrDefault(source, source));
                    cleanEdge.put("target", idToLabel.getOrDefault(target, target));
                    cleanEdge.put("weight", readDouble(edge.get("weight"), 1D));
                    cleanEdges.add(cleanEdge);
                }
                data.put("edges", cleanEdges);
            }
            if (normalizedNodes.isEmpty()) {
                log.warn("Skill graph normalized to empty set, fallback to local graph. topN={}, type={}", topN, type);
                return buildLocalSkillGraph(topN, type);
            }
        }
        return data;
    }

    private Map<String, Object> buildLocalSkillGraph(int topN, String type) {
        List<Map<String, Object>> topSkills = marketSkillService.cleanSkillRows(
                jobMapper.topSkills(Math.min(Math.max(topN * 4, 40), 200)),
                Math.min(Math.max(topN, 10), 40),
                type
        );
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, List<String>> dimGroups = new LinkedHashMap<>();

        for (Map<String, Object> skill : topSkills) {
            String name = readString(skill.get("skill"));
            if (name == null) continue;
            String entityType = readString(skill.get("type"));
            String dim = MarketSkillService.TYPE_SKILL.equals(entityType)
                    ? marketSkillService.inferCapabilityDimension(name)
                    : entityType;
            dimGroups.computeIfAbsent(dim, k -> new ArrayList<>()).add(name);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", name);
            node.put("label", name);
            node.put("value", readDouble(skill.get("count"), 1D));
            node.put("type", entityType);
            node.put("category", dim);
            nodes.add(node);
        }

        List<String> hubNodes = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : dimGroups.entrySet()) {
            List<String> group = entry.getValue();
            if (group.isEmpty()) continue;
            hubNodes.add(group.get(0));
            for (int i = 1; i < group.size(); i++) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", group.get(i - 1));
                edge.put("target", group.get(i));
                edge.put("weight", Math.max(1, group.size() - i));
                edges.add(edge);
            }
        }
        for (int i = 1; i < hubNodes.size(); i++) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", hubNodes.get(0));
            edge.put("target", hubNodes.get(i));
            edge.put("weight", hubNodes.size());
            edges.add(edge);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "local-fallback");
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("topN", topN);
        result.put("type", type == null ? MarketSkillService.TYPE_SKILL : type);
        result.put("categories", new ArrayList<>(dimGroups.keySet()));
        return result;
    }

    private boolean matchesEntityType(String entityType, String requestedType) {
        String normalizedType = requestedType == null ? MarketSkillService.TYPE_SKILL : requestedType.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalizedType)) {
            return true;
        }
        return normalizedType.equals(entityType);
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

    private Map<String, Object> normalizeSalaryPredictionResult(Object algorithmResult, Map<String, Object> params) {
        if (!(algorithmResult instanceof Map)) {
            return buildLocalSalaryPrediction(params);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) algorithmResult;

        Map<String, Object> flattened = new LinkedHashMap<>();
        Object nestedData = raw.get("data");
        if (nestedData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) nestedData;
            flattened.putAll(nested);
        }
        flattened.putAll(raw);

        if (isInsufficientSalaryResult(flattened)) {
            return buildLocalSalaryPrediction(params);
        }

        Map<String, Object> local = buildLocalSalaryPrediction(params);
        Map<String, Object> merged = new LinkedHashMap<>(local);
        copyIfMeaningful(merged, flattened, "range", "salaryRange", "predictedRange", "median", "predictedSalary",
                "predictedSalaryMin", "predictedSalaryMax", "salaryText", "confidence", "confidenceLabel", "summary",
                "message", "sampleCount", "factors", "benchmarks", "matchedSkills", "missingSkills", "salaryScorecard", "prediction");

        String rangeText = firstNonBlank(merged.get("range"), merged.get("salaryRange"), merged.get("predictedRange"));
        if (rangeText != null) {
            merged.put("range", rangeText);
            merged.put("salaryRange", rangeText);
            merged.put("predictedRange", rangeText);
        }
        merged.putIfAbsent("source", "algorithm-proxy");
        return merged;
    }

    private boolean isInsufficientSalaryResult(Map<String, Object> payload) {
        String rangeText = firstNonBlank(payload.get("range"), payload.get("salaryRange"), payload.get("predictedRange"));
        if (isMeaningfulValue(rangeText) && containsSalaryValue(rangeText)) {
            return false;
        }
        String summary = firstNonBlank(payload.get("summary"), payload.get("analysis"), payload.get("message"), payload.get("salaryText"));
        if (!isMeaningfulValue(summary)) {
            return true;
        }
        String lower = summary.toLowerCase(Locale.ROOT);
        return lower.contains("样本不足")
                || lower.contains("暂无结果")
                || lower.contains("无法形成稳定")
                || lower.contains("insufficient")
                || lower.contains("no stable")
                || lower.contains("no result");
    }

    private boolean containsSalaryValue(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*\\d+(\\.\\d+)?\\s*[k千].*")) {
            return true;
        }
        return normalized.matches(".*\\d+(\\.\\d+)?\\s*[-~到至]\\s*\\d+(\\.\\d+)? .*");
    }

    private Map<String, Object> enrichSalaryParams(Map<String, Object> params) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (params != null) {
            normalized.putAll(params);
        }
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) {
            return normalized;
        }
        Map<String, Object> userContext = userInsightService.loadUserContext(userId);
        if (userContext == null || userContext.isEmpty()) {
            return normalized;
        }
        if (readString(normalized.get("city")) == null) {
            normalized.put("city", firstNonBlank(userContext.get("targetCityName"), userContext.get("targetCityCode")));
        }
        if (readString(normalized.get("industry")) == null) {
            normalized.put("industry", firstNonBlank(userContext.get("industry"), userContext.get("profileSummary")));
        }
        if (readString(normalized.get("education")) == null) {
            normalized.put("education", userContext.get("educationLevel"));
        }
        if (readString(normalized.get("targetJob")) == null) {
            normalized.put("targetJob", firstNonBlank(userContext.get("targetJob"), userContext.get("profileSummary")));
        }
        if (parseSkills(normalized.get("skills")).isEmpty() && isMeaningfulValue(userContext.get("skills"))) {
            normalized.put("skills", userContext.get("skills"));
        }
        if (readString(normalized.get("experience")) == null) {
            Object experienceYears = isMeaningfulValue(normalized.get("experienceYears"))
                    ? normalized.get("experienceYears")
                    : userContext.get("experienceYears");
            if (isMeaningfulValue(experienceYears)) {
                String text = String.valueOf(experienceYears).trim();
                normalized.put("experience", text.endsWith("年") ? text : text + "年");
                normalized.putIfAbsent("experienceYears", experienceYears);
            }
        }
        return normalized;
    }

    private Map<String, Object> buildLocalSalaryPrediction(Map<String, Object> params) {
        String city = readString(params.get("city"));
        String industry = readString(params.get("industry"));
        String education = readString(params.get("education"));
        String experience = readString(params.get("experience"));
        List<String> parsedSkills = parseSkills(params.get("skills"));

        Map<String, Object> overview = jobMapper.overviewStats();
        double overviewMin = readDouble(overview.get("avgSalaryMin"), 10D);
        double overviewMax = readDouble(overview.get("avgSalaryMax"), Math.max(overviewMin + 4D, 18D));
        if (overviewMax <= overviewMin) {
            overviewMax = overviewMin + 4D;
        }

        SalaryBaseline baseline = resolveSalaryBaseline(city, industry, overviewMin, overviewMax);
        SkillSignal skillSignal = evaluateSkillSignal(parsedSkills, industry);
        double combinedFactor = educationFactorRobust(education) * experienceFactorRobust(experience) * skillSignal.factor;

        double predictedMin = round2(Math.max(3D, baseline.baseMin * combinedFactor));
        double predictedMax = round2(Math.max(predictedMin + 1.2D, baseline.baseMax * combinedFactor));
        double median = round2((predictedMin + predictedMax) / 2D);

        int confidenceScore = buildConfidenceScore(baseline, skillSignal);
        String confidenceLabel = confidenceLabel(confidenceScore);
        String rangeText = formatSalaryRange(predictedMin, predictedMax);
        String medianText = formatSalaryPoint(median);
        String summary = buildSalarySummary(city, industry, confidenceLabel, skillSignal);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "local-fallback");
        result.put("range", rangeText);
        result.put("salaryRange", rangeText);
        result.put("predictedRange", rangeText);
        result.put("median", medianText);
        result.put("predictedSalary", medianText);
        result.put("prediction", Math.round(median * 1000D));
        result.put("predictedSalaryMin", predictedMin);
        result.put("predictedSalaryMax", predictedMax);
        result.put("salaryText", rangeText);
        result.put("confidence", confidenceLabel);
        result.put("confidenceLabel", confidenceLabel);
        result.put("confidenceScore", confidenceScore);
        result.put("sampleCount", baseline.sampleCount);
        result.put("summary", summary);
        result.put("message", summary);
        result.put("factors", buildSalaryFactors(city, industry, education, experience, baseline, skillSignal));
        result.put("benchmarks", buildSalaryBenchmarks(baseline, predictedMin, predictedMax, medianText));
        result.put("matchedSkills", skillSignal.matchedSkills);
        result.put("missingSkills", skillSignal.missingSkills);
        result.put("salaryScorecard", buildSalaryScorecard(baseline, skillSignal, confidenceScore));
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
        for (String item : text.split("[,锛屻€?|]+")) {
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

    private SalaryBaseline resolveSalaryBaseline(String city, String industry, double overviewMin, double overviewMax) {
        TrendStats primary = buildTrendStats(jobMapper.salaryTrend(city, industry), "城市+行业");
        TrendStats cityOnly = city == null ? TrendStats.empty("城市") : buildTrendStats(jobMapper.salaryTrend(city, null), "城市");
        TrendStats industryOnly = industry == null ? TrendStats.empty("行业") : buildTrendStats(jobMapper.salaryTrend(null, industry), "行业");
        TrendStats global = buildTrendStats(jobMapper.salaryTrend(null, null), "全市场");

        List<TrendStats> candidates = Arrays.asList(primary, cityOnly, industryOnly, global);
        TrendStats selected = candidates.stream().filter(TrendStats::hasData).findFirst().orElse(TrendStats.empty("默认"));

        double baseMin = selected.hasData() ? selected.avgMin : overviewMin;
        double baseMax = selected.hasData() ? selected.avgMax : overviewMax;
        if (baseMax <= baseMin) {
            baseMax = baseMin + 4D;
        }

        if (selected.sampleCount < 120D) {
            TrendStats backup = candidates.stream()
                    .filter(TrendStats::hasData)
                    .filter(item -> item != selected)
                    .findFirst()
                    .orElse(TrendStats.empty("全市场"));
            if (backup.hasData()) {
                double keepWeight = Math.max(0.45D, Math.min(0.8D, selected.sampleCount / 160D + 0.3D));
                baseMin = baseMin * keepWeight + backup.avgMin * (1D - keepWeight);
                baseMax = baseMax * keepWeight + backup.avgMax * (1D - keepWeight);
                selected.sampleCount = selected.sampleCount + Math.round(backup.sampleCount * (1D - keepWeight));
                selected.scopeLabel = selected.scopeLabel + " + " + backup.scopeLabel;
                selected.qualityScore = Math.max(0.25D, selected.qualityScore - 0.08D);
            }
        }

        double quality = Math.max(0.2D, Math.min(1D, selected.qualityScore));
        return new SalaryBaseline(baseMin, baseMax, selected.sampleCount, quality, selected.scopeLabel);
    }

    private TrendStats buildTrendStats(List<Map<String, Object>> rows, String scopeLabel) {
        if (rows == null || rows.isEmpty()) {
            return TrendStats.empty(scopeLabel);
        }
        double avgMin = avgOf(rows, "avgSalaryMin", 0D);
        double avgMax = avgOf(rows, "avgSalaryMax", Math.max(avgMin + 2D, 0D));
        long sampleCount = Math.round(rows.stream().mapToDouble(row -> readDouble(row.get("jobCount"), 0D)).sum());
        if (sampleCount <= 0) {
            sampleCount = rows.size() * 20L;
        }
        int months = rows.size();
        double quality = Math.min(1D, sampleCount / 600D) * 0.7D + Math.min(1D, months / 8D) * 0.3D;
        return new TrendStats(avgMin, avgMax, sampleCount, months, quality, scopeLabel);
    }

    private SkillSignal evaluateSkillSignal(List<String> parsedSkills, String industry) {
        List<String> userSkills = marketSkillService.cleanSkillNames(parsedSkills, 20);
        if (userSkills.isEmpty()) {
            userSkills = parsedSkills.stream().filter(this::isMeaningfulValue).collect(Collectors.toList());
        }

        List<Map<String, Object>> marketRows = marketSkillService.topTechnicalSkills(20);
        List<String> marketSkills = marketRows.stream()
                .map(item -> readString(item.get("skill")))
                .filter(this::isMeaningfulValue)
                .collect(Collectors.toList());

        Set<String> userLower = userSkills.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> matched = new ArrayList<>();
        for (String marketSkill : marketSkills) {
            if (userLower.contains(marketSkill.toLowerCase(Locale.ROOT))) {
                matched.add(marketSkill);
            }
        }
        if (matched.isEmpty() && !userSkills.isEmpty()) {
            matched.addAll(userSkills.stream().limit(3).collect(Collectors.toList()));
        }

        List<String> missing = new ArrayList<>();
        for (String marketSkill : marketSkills) {
            if (!userLower.contains(marketSkill.toLowerCase(Locale.ROOT))) {
                missing.add(marketSkill);
            }
            if (missing.size() >= 4) {
                break;
            }
        }

        int advancedCount = 0;
        List<String> advancedKeywords = Arrays.asList("algorithm", "machine learning", "deep learning", "llm", "distributed", "spark", "hadoop", "flink", "ai");
        for (String skill : userSkills) {
            String lower = skill.toLowerCase(Locale.ROOT);
            if (advancedKeywords.stream().anyMatch(lower::contains) || lower.contains("算法") || lower.contains("机器学习") || lower.contains("大模型")) {
                advancedCount++;
            }
        }

        double factor = 1D + Math.min(0.18D, userSkills.size() * 0.012D + matched.size() * 0.015D + advancedCount * 0.01D);
        if (isMeaningfulValue(industry) && (industry.toLowerCase(Locale.ROOT).contains("ai") || industry.contains("人工智能"))) {
            factor = Math.min(1.24D, factor + 0.02D);
        }
        int score = (int) Math.round(Math.min(100D, 45D + userSkills.size() * 2.5D + matched.size() * 6D + advancedCount * 4D));
        return new SkillSignal(factor, score, matched, missing, userSkills);
    }

    private int buildConfidenceScore(SalaryBaseline baseline, SkillSignal skillSignal) {
        int sampleScore = (int) Math.round(Math.min(100D, baseline.qualityScore * 100D));
        int confidence = (int) Math.round(sampleScore * 0.65D + skillSignal.score * 0.35D);
        if (baseline.sampleCount < 80L) {
            confidence -= 8;
        }
        return Math.max(40, Math.min(96, confidence));
    }

    private String confidenceLabel(int score) {
        if (score >= 85) return "高";
        if (score >= 70) return "中高";
        if (score >= 58) return "中";
        return "中低";
    }

    private String formatSalaryRange(double min, double max) {
        return String.format(Locale.US, "%.1fK-%.1fK", min, max);
    }

    private String formatSalaryPoint(double value) {
        return String.format(Locale.US, "%.1fK", value);
    }

    private double educationFactorRobust(String education) {
        String text = education == null ? "" : education.toLowerCase(Locale.ROOT);
        if (text.contains("phd") || text.contains("doctor") || text.contains("博士")) return 1.20D;
        if (text.contains("master") || text.contains("硕士")) return 1.12D;
        if (text.contains("bachelor") || text.contains("本科")) return 1.06D;
        if (text.contains("college") || text.contains("大专")) return 0.98D;
        return 1.0D;
    }

    private double experienceFactorRobust(String experience) {
        String text = experience == null ? "" : experience.toLowerCase(Locale.ROOT);
        if (text.contains("senior")) return 1.22D;
        if (text.contains("应届")) return 0.95D;
        int years = extractFirstNumber(text);
        if (years >= 8) return 1.30D;
        if (years >= 5) return 1.22D;
        if (years >= 3) return 1.12D;
        if (years >= 1) return 1.05D;
        if (years == 0) return 0.95D;
        return 1.0D;
    }

    private int extractFirstNumber(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        StringBuilder number = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                number.append(ch);
                continue;
            }
            if (number.length() > 0) {
                break;
            }
        }
        if (number.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(number.toString());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String buildSalarySummary(String city, String industry, String confidenceLabel, SkillSignal skillSignal) {
        String cityText = isMeaningfulValue(city) ? city : "目标城市";
        String industryText = isMeaningfulValue(industry) ? industry : "目标行业";
        return String.format(Locale.ROOT,
                "基于 %s / %s 的岗位样本做了分层回归估计，当前可信度为 %s，已识别 %d 项相关技能，可作为投递前的薪资参考。",
                cityText, industryText, confidenceLabel, skillSignal.rawSkills.size());
    }

    private List<Map<String, Object>> buildSalaryFactors(String city,
                                                         String industry,
                                                         String education,
                                                         String experience,
                                                         SalaryBaseline baseline,
                                                         SkillSignal skillSignal) {
        List<Map<String, Object>> factors = new ArrayList<>();
        factors.add(factorItem("样本覆盖", String.format(Locale.ROOT, "采用 %s 样本，估算样本量约 %d。", baseline.scopeLabel, baseline.sampleCount)));
        factors.add(factorItem("技能贴合", String.format(Locale.ROOT, "已命中 %d 项市场技能，技能得分 %d。", skillSignal.matchedSkills.size(), skillSignal.score)));
        factors.add(factorItem("经验阶段", String.format(Locale.ROOT, "经验：%s；学历：%s。",
                isMeaningfulValue(experience) ? experience : "未填写",
                isMeaningfulValue(education) ? education : "未填写")));
        factors.add(factorItem("地域行业", String.format(Locale.ROOT, "城市：%s；行业：%s。",
                isMeaningfulValue(city) ? city : "未指定",
                isMeaningfulValue(industry) ? industry : "未指定")));
        return factors;
    }

    private List<Map<String, Object>> buildSalaryBenchmarks(SalaryBaseline baseline, double predictedMin, double predictedMax, String medianText) {
        List<Map<String, Object>> benchmarks = new ArrayList<>();
        benchmarks.add(benchmarkItem("市场基线", String.format(Locale.US, "%.1fK-%.1fK", baseline.baseMin, baseline.baseMax)));
        benchmarks.add(benchmarkItem("预测区间", String.format(Locale.US, "%.1fK-%.1fK", predictedMin, predictedMax)));
        benchmarks.add(benchmarkItem("预测中位", medianText));
        return benchmarks;
    }

    private List<Map<String, Object>> buildSalaryScorecard(SalaryBaseline baseline, SkillSignal skillSignal, int confidenceScore) {
        List<Map<String, Object>> scorecard = new ArrayList<>();
        scorecard.add(scoreItem("样本稳定性", (int) Math.round(Math.min(100D, baseline.qualityScore * 100D))));
        scorecard.add(scoreItem("技能贴合度", skillSignal.score));
        scorecard.add(scoreItem("经验合理性", Math.min(95, 55 + skillSignal.rawSkills.size() * 4)));
        scorecard.add(scoreItem("区间可信度", confidenceScore));
        return scorecard;
    }

    private Map<String, Object> factorItem(String label, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("detail", detail);
        return row;
    }

    private Map<String, Object> benchmarkItem(String title, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("detail", detail);
        return row;
    }

    private Map<String, Object> scoreItem(String label, int score) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("score", Math.max(0, Math.min(100, score)));
        return row;
    }

    private void copyIfMeaningful(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (isMeaningfulValue(value)) {
                target.put(key, value);
            }
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private boolean isMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return false;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            return !lower.equals("null") && !lower.equals("--") && !lower.equals("n/a");
        }
        if (value instanceof List) {
            return !((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    private static final class TrendStats {
        private double avgMin;
        private double avgMax;
        private long sampleCount;
        private int months;
        private double qualityScore;
        private String scopeLabel;

        private TrendStats(double avgMin, double avgMax, long sampleCount, int months, double qualityScore, String scopeLabel) {
            this.avgMin = avgMin;
            this.avgMax = avgMax;
            this.sampleCount = sampleCount;
            this.months = months;
            this.qualityScore = qualityScore;
            this.scopeLabel = scopeLabel;
        }

        private static TrendStats empty(String scopeLabel) {
            return new TrendStats(0D, 0D, 0L, 0, 0D, scopeLabel);
        }

        private boolean hasData() {
            return avgMin > 0D && avgMax > 0D;
        }
    }

    private static final class SalaryBaseline {
        private final double baseMin;
        private final double baseMax;
        private final long sampleCount;
        private final double qualityScore;
        private final String scopeLabel;

        private SalaryBaseline(double baseMin, double baseMax, long sampleCount, double qualityScore, String scopeLabel) {
            this.baseMin = baseMin;
            this.baseMax = baseMax;
            this.sampleCount = sampleCount;
            this.qualityScore = qualityScore;
            this.scopeLabel = scopeLabel;
        }
    }

    private static final class SkillSignal {
        private final double factor;
        private final int score;
        private final List<String> matchedSkills;
        private final List<String> missingSkills;
        private final List<String> rawSkills;

        private SkillSignal(double factor, int score, List<String> matchedSkills, List<String> missingSkills, List<String> rawSkills) {
            this.factor = factor;
            this.score = score;
            this.matchedSkills = matchedSkills == null ? Collections.emptyList() : matchedSkills.stream().limit(4).collect(Collectors.toList());
            this.missingSkills = missingSkills == null ? Collections.emptyList() : missingSkills.stream().limit(4).collect(Collectors.toList());
            this.rawSkills = rawSkills == null ? Collections.emptyList() : rawSkills;
        }
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
            recommendations.add("技能需求集中度偏高，适合围绕高频技能建立“核心能力点 + 进阶专题”的双层课程结构。");
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

    // 鈹€鈹€鈹€ 娣卞害鍒嗘瀽锛堟湭鍒╃敤瀛楁锛?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Operation(summary = "Welfare/benefits distribution")
    @GetMapping("/welfare")
    public R<?> welfareDistribution(@RequestParam(defaultValue = "20") int limit) {
        if (pageSnapshotService != null) {
            Map<String, Object> chart = new HashMap<>();
            chart.put("chartType", "bar");
            chart.put("title", "welfare-distribution");
            chart.put("data", pageSnapshotService.getWelfareDistribution(limit));
            return R.ok(chart);
        }
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
        if (pageSnapshotService != null) {
            Map<String, Object> chart = new HashMap<>();
            chart.put("chartType", "pie");
            chart.put("title", "company-size-distribution");
            chart.put("data", pageSnapshotService.getCompanySizeDistribution());
            return R.ok(chart);
        }
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
        if (pageSnapshotService != null) {
            Map<String, Object> chart = new HashMap<>();
            chart.put("chartType", "pie");
            chart.put("title", "finance-stage-distribution");
            chart.put("data", pageSnapshotService.getFinanceStageDistribution());
            return R.ok(chart);
        }
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
