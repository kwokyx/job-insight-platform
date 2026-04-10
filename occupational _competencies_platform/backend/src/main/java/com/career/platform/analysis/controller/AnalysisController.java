package com.career.platform.analysis.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.job.mapper.JobPostingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Tag(name = "Data Analysis", description = "Overview, salary, skills, region, and algorithm proxy APIs")
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final JobPostingMapper jobMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient algorithmWebClient;

    public AnalysisController(JobPostingMapper jobMapper,
                              RedisTemplate<String, Object> redisTemplate,
                              @Qualifier("algorithmWebClient") WebClient algorithmWebClient) {
        this.jobMapper = jobMapper;
        this.redisTemplate = redisTemplate;
        this.algorithmWebClient = algorithmWebClient;
    }

    @Operation(summary = "Overview dashboard")
    @GetMapping("/overview")
    @SuppressWarnings("unchecked")
    public R<?> overview() {
        String cacheKey = "cache:analysis:overview";
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return R.ok(cached);
        }

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> stats = jobMapper.overviewStats();
        data.put("totalJobs", jobMapper.selectCount(null));
        data.put("avgSalaryMin", stats.get("avgSalaryMin"));
        data.put("avgSalaryMax", stats.get("avgSalaryMax"));
        data.put("topCities", jobMapper.aggregateByCity(10));
        data.put("topIndustries", jobMapper.aggregateByIndustry(10));
        data.put("topSkills", jobMapper.topSkills(10));
        data.put("educationDistribution", jobMapper.aggregateByEducation());
        data.put("experienceDistribution", jobMapper.aggregateByExperience());

        redisTemplate.opsForValue().set(cacheKey, data, 10, TimeUnit.MINUTES);
        return R.ok(data);
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
            return R.fail("Algorithm service unavailable: " + e.getMessage());
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
            return R.fail("Algorithm service unavailable: " + e.getMessage());
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
            return R.fail("Algorithm service unavailable: " + e.getMessage());
        }
    }
}
