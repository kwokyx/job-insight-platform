package com.career.platform.report.service;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.subscription.entity.Notification;
import com.career.platform.subscription.mapper.NotificationMapper;
import com.career.platform.warehouse.service.SupplyDemandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final AnalysisReportMapper reportMapper;
    private final AnalysisTaskMapper taskMapper;
    private final JobPostingMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final LlmReportWriterService llmReportWriterService;
    private final SupplyDemandService supplyDemandService;
    private final NotificationMapper notificationMapper;
    private final UserInsightService userInsightService;

    @Async("reportExecutor")
    public void executeReportGeneration(Long taskId, String reportType, String reportName, Long userId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Report task not found: {}", taskId);
            return;
        }

        try {
            updateTask(task, "RUNNING", 10, null);

            Map<String, Object> analysisData = new LinkedHashMap<>();
            Map<String, Object> overview = new LinkedHashMap<>(jobMapper.overviewStats());
            overview.put("totalJobs", jobMapper.selectCount(null));
            analysisData.put("overview", overview);
            task.setProgress(30);
            taskMapper.updateById(task);

            String normalizedType = reportType == null ? "COMPREHENSIVE" : reportType.toUpperCase();
            switch (normalizedType) {
                case "SALARY":
                    buildSalaryReport(analysisData);
                    break;
                case "SKILL":
                    buildSkillReport(analysisData);
                    break;
                case "INDUSTRY":
                    buildIndustryReport(analysisData);
                    break;
                case "SUPPLY_DEMAND":
                    analysisData.put("supplyDemand", supplyDemandService.analyzeSkyDemandGap(null));
                    buildComprehensiveReport(analysisData);
                    break;
                default:
                    buildComprehensiveReport(analysisData);
                    break;
            }

            task.setProgress(70);
            taskMapper.updateById(task);

            Map<String, Object> userContext = userInsightService.loadUserContext(userId);
            analysisData.put("userContext", userContext);

            Map<String, Object> narrative = llmReportWriterService.generateNarrative(analysisData, normalizedType, userContext);
            String diagnosticSummary = sanitizeNarrativeText(String.valueOf(narrative.getOrDefault("summary", "")));
            analysisData.put("chartInsights", sanitizeNarrativeList(narrative.getOrDefault("chartInsights", Collections.emptyList())));
            analysisData.put("recommendations", sanitizeNarrativeList(narrative.getOrDefault("recommendations", Collections.emptyList())));
            analysisData.put("diagnosticSummary", diagnosticSummary);
            analysisData.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            task.setProgress(85);
            taskMapper.updateById(task);

            AnalysisReport report = new AnalysisReport();
            report.setTaskId(taskId);
            report.setReportName(reportName);
            report.setReportType(normalizedType);
            report.setReportFormat("JSON");
            report.setDescription(diagnosticSummary);
            report.setAnalysisData(objectMapper.writeValueAsString(analysisData));
            report.setIsPublic(0);
            report.setViewCount(0);
            report.setDownloadCount(0);
            report.setGeneratedBy(userId);
            report.setGeneratedAt(LocalDateTime.now());
            report.setCreatedAt(LocalDateTime.now());
            reportMapper.insert(report);

            Map<String, Object> resultSummary = new HashMap<>();
            resultSummary.put("reportId", report.getId());
            resultSummary.put("reportType", normalizedType);
            resultSummary.put("hasDiagnosticSummary", true);
            resultSummary.put("recommendationCount", ((List<?>) analysisData.getOrDefault("recommendations", Collections.emptyList())).size());
            task.setResultSummary(objectMapper.writeValueAsString(resultSummary));
            updateTask(task, "SUCCESS", 100, null);
            createReportReadyNotification(report, userId);
            log.info("Report generation completed: taskId={}, reportId={}", taskId, report.getId());
        } catch (Exception e) {
            log.error("Report generation failed: taskId={}", taskId, e);
            updateTask(task, "FAILED", task.getProgress() == null ? 0 : task.getProgress(), e.getMessage());
        }
    }

    private void buildSalaryReport(Map<String, Object> data) {
        data.put("salaryByCity", jobMapper.aggregateByCity(20));
        data.put("salaryByIndustry", jobMapper.aggregateByIndustry(20));
        data.put("salaryByEducation", jobMapper.aggregateByEducation());
        data.put("salaryByExperience", jobMapper.aggregateByExperience());
        data.put("salaryTrend", jobMapper.salaryTrend(null, null));
        data.put("topSkills", jobMapper.topSkills(15));
        data.put("salaryComparison", buildSalaryComparison());
    }

    private void buildSkillReport(Map<String, Object> data) {
        data.put("topSkills", jobMapper.topSkills(30));
        data.put("skillsByIndustry", jobMapper.aggregateByIndustry(20));
        data.put("educationDist", jobMapper.aggregateByEducation());
        data.put("experienceDist", jobMapper.aggregateByExperience());
    }

    private void buildIndustryReport(Map<String, Object> data) {
        data.put("industries", jobMapper.aggregateByIndustry(30));
        data.put("topCities", jobMapper.aggregateByCity(20));
        data.put("educationDist", jobMapper.aggregateByEducation());
        data.put("salaryTrend", jobMapper.salaryTrend(null, null));
    }

    private void buildComprehensiveReport(Map<String, Object> data) {
        data.put("topCities", jobMapper.aggregateByCity(15));
        data.put("topIndustries", jobMapper.aggregateByIndustry(15));
        data.put("topSkills", jobMapper.topSkills(20));
        data.put("educationDist", jobMapper.aggregateByEducation());
        data.put("experienceDist", jobMapper.aggregateByExperience());
        data.put("salaryTrend", jobMapper.salaryTrend(null, null));
        data.put("hotJobs", jobMapper.hotJobs(10));
        data.put("salaryComparison", buildSalaryComparison());
    }

    private Map<String, Object> buildSalaryComparison() {
        Map<String, Object> comparison = new LinkedHashMap<>();
        List<Map<String, Object>> byCities = jobMapper.aggregateByCity(5);
        List<Map<String, Object>> byEdu = jobMapper.aggregateByEducation();

        if (!byCities.isEmpty()) {
            comparison.put("topCity", byCities.get(0).get("city"));
            comparison.put("topCitySalary", byCities.get(0).get("avgSalary"));
        }

        if (!byEdu.isEmpty()) {
            Map<String, Object> eduSalary = new LinkedHashMap<>();
            for (Map<String, Object> row : byEdu) {
                eduSalary.put(String.valueOf(row.get("education")), row.get("avgSalary"));
            }
            comparison.put("educationPremium", eduSalary);
        }
        return comparison;
    }

    private void createReportReadyNotification(AnalysisReport report, Long userId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle("分析报告已生成");
        notification.setContent("报告《" + report.getReportName() + "》已生成，可在报告中心下载或导出 PDF。");
        notification.setNotifyType("REPORT_READY");
        notification.setRefId(report.getId());
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    private void updateTask(AnalysisTask task, String status, Integer progress, String errorMessage) {
        task.setStatus(status);
        task.setProgress(progress);
        if ("RUNNING".equals(status) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        }
        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }
        taskMapper.updateById(task);
    }

    private String sanitizeNarrativeText(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text.replaceAll("(?is)<think>.*?</think>", "");
        sanitized = sanitized.replace("</think>", "");
        return sanitized.trim();
    }

    private List<String> sanitizeNarrativeList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) value;
        List<String> cleaned = new java.util.ArrayList<>();
        for (Object item : raw) {
            String s = sanitizeNarrativeText(String.valueOf(item));
            if (!s.isEmpty()) {
                cleaned.add(s);
            }
        }
        return cleaned;
    }
}
