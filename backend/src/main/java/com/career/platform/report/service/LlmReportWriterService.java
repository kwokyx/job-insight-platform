package com.career.platform.report.service;

import com.career.platform.ai.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmReportWriterService {

    private final LlmClient llmClient;

    public Map<String, Object> generateNarrative(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Map<String, Object> fallback = buildFallbackNarrative(analysisData, reportType, userContext);

        String systemPrompt = "You are a senior career analytics advisor. "
                + "Use only provided data. Do not fabricate numbers. "
                + "Return practical suggestions tied to market data and user profile. "
                + "Do not output hidden reasoning, internal analysis, or meta commentary. "
                + "Return strict JSON only.";
        String userPrompt = buildPrompt(analysisData, reportType, userContext);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);

        try {
            String response = llmClient.chat(systemPrompt, messages);
            if (!StringUtils.hasText(response)) {
                return fallback;
            }
            Map<String, Object> parsed = parseNarrativeJson(response);
            if (parsed.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>(fallback);
                result.put("summary", response.trim());
                return result;
            }
            Map<String, Object> result = new LinkedHashMap<>(fallback);
            result.putAll(parsed);
            return result;
        } catch (Exception e) {
            log.warn("LLM report narrative generation failed, use fallback: {}", e.getMessage());
            return fallback;
        }
    }

    public String generateDiagnosticSummary(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Map<String, Object> narrative = generateNarrative(analysisData, reportType, userContext);
        Object summary = narrative.get("summary");
        return summary == null ? "" : String.valueOf(summary);
    }

    private String buildPrompt(Map<String, Object> analysisData, String reportType, Map<String, Object> userContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Build a concise report narrative with 3 parts:\n");
        prompt.append("1) market diagnosis with explicit data references\n");
        prompt.append("2) profile fit and risk points for this user\n");
        prompt.append("3) 3-5 actionable suggestions in platform context\n\n");
        prompt.append("Return JSON with keys: summary, chartInsights, recommendations.\n");
        prompt.append("summary must be a concise Chinese report body.\n");
        prompt.append("chartInsights must be an array of 3-5 short strings.\n");
        prompt.append("recommendations must be an array of 3-5 short actionable strings.\n");
        prompt.append("Report type: ").append(reportType).append("\n");
        prompt.append("User context: ").append(String.valueOf(userContext)).append("\n");
        prompt.append("Data: ").append(String.valueOf(analysisData)).append("\n");
        prompt.append("Keep response compact and practical. Output JSON only, without markdown fences.");
        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseNarrativeJson(String response) {
        try {
            String trimmed = response.trim();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return Collections.emptyMap();
            }
            String json = trimmed.substring(start, end + 1);
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFallbackNarrative(
            Map<String, Object> analysisData,
            String reportType,
            Map<String, Object> userContext
    ) {
        Map<String, Object> overview = asMap(analysisData.get("overview"));
        List<Map<String, Object>> topSkills = asList(analysisData.get("topSkills"));
        List<Map<String, Object>> topCities = asList(analysisData.get("topCities"));
        List<String> userSkills = toStringList(userContext.get("skills"));
        String targetRole = String.valueOf(userContext.getOrDefault("profileSummary", "not set"));

        String totalJobs = String.valueOf(overview.getOrDefault("totalJobs", "N/A"));
        String salaryMin = String.valueOf(overview.getOrDefault("avgSalaryMin", "N/A"));
        String salaryMax = String.valueOf(overview.getOrDefault("avgSalaryMax", "N/A"));
        String topSkill = topSkills.isEmpty() ? "N/A" : String.valueOf(topSkills.get(0).get("skill"));
        String topCity = topCities.isEmpty() ? "N/A" : String.valueOf(topCities.get(0).get("city"));

        List<String> chartInsights = new ArrayList<>();
        chartInsights.add("Total jobs in sample: " + totalJobs);
        chartInsights.add("Average salary band: " + salaryMin + "K - " + salaryMax + "K");
        chartInsights.add("Top skill demand: " + topSkill);
        chartInsights.add("Top hiring city: " + topCity);

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Align resume keywords with top demand skills in this report.");
        recommendations.add("Prioritize opportunities in high-density cities and industries from the report.");
        recommendations.add("Use the platform skill-gap and job-match modules to close missing skills weekly.");
        if (StringUtils.hasText(targetRole) && !"not set".equalsIgnoreCase(targetRole)) {
            recommendations.add("Build a targeted plan for role: " + targetRole + ".");
        }
        if (!userSkills.isEmpty()) {
            recommendations.add("Current strengths: " + String.join(", ", userSkills.subList(0, Math.min(5, userSkills.size()))) + ".");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary",
                "Report type " + reportType + " shows a market with " + totalJobs
                        + " jobs and average salary range " + salaryMin + "K-" + salaryMax + "K. "
                        + "Demand is concentrated around " + topSkill + " and city " + topCity + ". "
                        + "Use the recommendations to improve role fit and execution quality.");
        result.put("chartInsights", chartInsights);
        result.put("recommendations", recommendations);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Object> raw = (List<Object>) value;
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
