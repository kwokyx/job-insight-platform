package com.career.platform.report.service;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Async("reportExecutor")
    public void executeReportGeneration(Long taskId, String reportType, String reportName, Long userId) {
        try {
            AnalysisTask task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Report task not found: {}", taskId);
                return;
            }

            task.setStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(10);
            taskMapper.updateById(task);

            Map<String, Object> analysisData = new LinkedHashMap<>();
            task.setProgress(30);
            taskMapper.updateById(task);

            // 基础统计数据（所有类型都需要）
            Map<String, Object> overview = jobMapper.overviewStats();
            overview.put("totalJobs", jobMapper.selectCount(null));

            String upperType = reportType.toUpperCase();
            switch (upperType) {
                case "SALARY":
                    buildSalaryReport(analysisData, overview);
                    break;
                case "SKILL":
                    buildSkillReport(analysisData, overview);
                    break;
                case "INDUSTRY":
                    buildIndustryReport(analysisData, overview);
                    break;
                default:
                    buildComprehensiveReport(analysisData, overview);
                    break;
            }

            task.setProgress(70);
            taskMapper.updateById(task);

            // 生成摘要文本
            String summary = generateReportSummary(reportType, analysisData);
            analysisData.put("summary", summary);
            analysisData.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            task.setProgress(85);
            taskMapper.updateById(task);

            AnalysisReport report = new AnalysisReport();
            report.setTaskId(taskId);
            report.setReportName(reportName);
            report.setReportType(reportType);
            report.setReportFormat("JSON");
            report.setDescription(summary);
            report.setAnalysisData(objectMapper.writeValueAsString(analysisData));
            report.setIsPublic(0);
            report.setViewCount(0);
            report.setDownloadCount(0);
            report.setGeneratedBy(userId);
            report.setGeneratedAt(LocalDateTime.now());
            report.setCreatedAt(LocalDateTime.now());
            reportMapper.insert(report);

            task.setStatus("SUCCESS");
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());

            Map<String, Object> resultSummary = new HashMap<>();
            resultSummary.put("reportId", report.getId());
            resultSummary.put("dataKeys", analysisData.keySet());
            task.setResultSummary(objectMapper.writeValueAsString(resultSummary));
            taskMapper.updateById(task);

            log.info("Report generation completed: taskId={}, reportId={}", taskId, report.getId());
        } catch (Exception e) {
            log.error("Report generation failed: taskId={}", taskId, e);
            AnalysisTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
                task.setCompletedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }
    }

    // ─── 按类型构建报告数据 ──────────────────

    private void buildSalaryReport(Map<String, Object> data, Map<String, Object> overview) {
        data.put("overview", overview);
        data.put("salaryByCity", jobMapper.aggregateByCity(20));
        data.put("salaryByIndustry", jobMapper.aggregateByIndustry(20));
        data.put("salaryByEducation", jobMapper.aggregateByEducation());
        data.put("salaryByExperience", jobMapper.aggregateByExperience());
        // 薪资趋势
        data.put("salaryTrend", jobMapper.salaryTrend(null, null));
        // 高薪TOP技能
        data.put("topSkills", jobMapper.topSkills(15));
        // 对比分析
        Map<String, Object> comparison = buildSalaryComparison();
        data.put("salaryComparison", comparison);
    }

    private void buildSkillReport(Map<String, Object> data, Map<String, Object> overview) {
        data.put("overview", overview);
        data.put("topSkills", jobMapper.topSkills(30));
        data.put("skillsByIndustry", jobMapper.aggregateByIndustry(20));
        // 技能与学历的交叉分析
        data.put("educationDist", jobMapper.aggregateByEducation());
        data.put("experienceDist", jobMapper.aggregateByExperience());
    }

    private void buildIndustryReport(Map<String, Object> data, Map<String, Object> overview) {
        data.put("overview", overview);
        data.put("industries", jobMapper.aggregateByIndustry(30));
        data.put("topCities", jobMapper.aggregateByCity(20));
        data.put("educationDist", jobMapper.aggregateByEducation());
        // 行业薪资趋势
        data.put("salaryTrend", jobMapper.salaryTrend(null, null));
    }

    private void buildComprehensiveReport(Map<String, Object> data, Map<String, Object> overview) {
        data.put("overview", overview);
        data.put("topCities", jobMapper.aggregateByCity(15));
        data.put("topIndustries", jobMapper.aggregateByIndustry(15));
        data.put("topSkills", jobMapper.topSkills(20));
        data.put("educationDist", jobMapper.aggregateByEducation());
        data.put("experienceDist", jobMapper.aggregateByExperience());
        data.put("salaryTrend", jobMapper.salaryTrend(null, null));
        // 热门职位
        data.put("hotJobs", jobMapper.hotJobs(10));
        // 薪资对比
        data.put("salaryComparison", buildSalaryComparison());
    }

    // ─── 薪资对比分析 ──────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSalaryComparison() {
        Map<String, Object> comparison = new LinkedHashMap<>();

        List<Map<String, Object>> byCities = jobMapper.aggregateByCity(5);
        List<Map<String, Object>> byEdu = jobMapper.aggregateByEducation();

        // 城市间薪资排名
        if (!byCities.isEmpty()) {
            comparison.put("topCity", byCities.get(0).get("city"));
            comparison.put("topCitySalary", byCities.get(0).get("avgSalary"));
        }

        // 学历溢价分析
        if (byEdu.size() >= 2) {
            Map<String, Double> eduSalary = new LinkedHashMap<>();
            for (Map<String, Object> row : byEdu) {
                String edu = String.valueOf(row.get("education"));
                Object avgObj = row.get("avgSalary");
                if (avgObj != null) {
                    eduSalary.put(edu, Double.parseDouble(avgObj.toString()));
                }
            }
            comparison.put("educationPremium", eduSalary);
        }

        return comparison;
    }

    // ─── 报告摘要生成 ───────────────────────

    @SuppressWarnings("unchecked")
    private String generateReportSummary(String reportType, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        Map<String, Object> overview = (Map<String, Object>) data.get("overview");
        if (overview != null) {
            sb.append("平台共收录 ").append(overview.get("totalJobs")).append(" 个职位，");
            if (overview.get("avgSalaryMin") != null) {
                sb.append("平均薪资区间 ").append(overview.get("avgSalaryMin"))
                  .append("K~").append(overview.get("avgSalaryMax")).append("K/月。");
            }
        }

        String upperType = reportType.toUpperCase();
        switch (upperType) {
            case "SALARY":
                sb.append("本报告详细分析了不同城市、行业、学历、经验维度的薪资差异及趋势。");
                break;
            case "SKILL":
                sb.append("本报告展示了市场热门技能需求排行及行业技能分布。");
                break;
            case "INDUSTRY":
                sb.append("本报告分析了各行业的就业规模、薪资水平及发展趋势。");
                break;
            default:
                sb.append("本报告从多维度综合分析了当前就业市场概况。");
                break;
        }

        return sb.toString();
    }
}
