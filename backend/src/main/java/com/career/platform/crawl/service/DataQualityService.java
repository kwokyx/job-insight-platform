package com.career.platform.crawl.service;

import com.career.platform.crawl.entity.JobHistory;
import com.career.platform.crawl.mapper.JobHistoryMapper;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataQualityService {

    private final JdbcTemplate jdbcTemplate;
    private final JobPostingMapper jobMapper;
    private final JobHistoryMapper jobHistoryMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getQualityReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalJobs = jobMapper.selectCount(null);
        report.put("totalJobs", totalJobs);

        Map<String, Object> completeness = new LinkedHashMap<>();
        completeness.put("titleRate", calcNonNullRate("title"));
        completeness.put("companyNameRate", calcNonNullRate("company_name"));
        completeness.put("salaryRate", calcFieldRate("salary_min IS NOT NULL AND salary_min > 0"));
        completeness.put("educationRate", calcNonNullRate("education_need"));
        completeness.put("experienceRate", calcNonNullRate("experience_year"));
        completeness.put("descriptionRate", calcNonNullRate("position_info"));
        completeness.put("industryRate", calcNonNullRate("job_classification"));
        report.put("completeness", completeness);

        List<Map<String, Object>> freshness = jdbcTemplate.queryForList(
                "SELECT " +
                        "CASE " +
                        "  WHEN publish_date >= CURDATE() - INTERVAL 7 DAY THEN '7d' " +
                        "  WHEN publish_date >= CURDATE() - INTERVAL 30 DAY THEN '30d' " +
                        "  WHEN publish_date >= CURDATE() - INTERVAL 90 DAY THEN '90d' " +
                        "  ELSE 'older' " +
                        "END AS period, COUNT(*) AS count " +
                        "FROM biz_job_posting " +
                        "WHERE publish_date IS NOT NULL " +
                        "GROUP BY period " +
                        "ORDER BY FIELD(period, '7d', '30d', '90d', 'older')"
        );
        report.put("freshness", freshness);

        Long staleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM biz_job_posting " +
                        "WHERE publish_date < CURDATE() - INTERVAL 90 DAY",
                Long.class
        );
        report.put("suspectedZombieJobs", staleCount);
        report.put("suspectedZombieJobRate", totalJobs > 0
                ? String.format("%.2f%%", staleCount * 100.0 / totalJobs) : "0%");

        Long salaryAnomalyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM biz_job_posting " +
                        "WHERE salary_min IS NOT NULL AND (salary_min > 200 OR (salary_max IS NOT NULL AND salary_max < 1))",
                Long.class
        );
        report.put("salaryAnomalyCount", salaryAnomalyCount);

        Long duplicateCandidates = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duplicate_count), 0) FROM crawl_task",
                Long.class
        );
        report.put("duplicateCandidates", duplicateCandidates);

        List<Map<String, Object>> sourceDistribution = jdbcTemplate.queryForList(
                "SELECT " +
                        "CASE " +
                        "  WHEN url LIKE '%zhaopin%' THEN 'zhaopin' " +
                        "  WHEN url LIKE '%51job%' THEN '51job' " +
                        "  WHEN url LIKE '%zhipin%' THEN 'boss' " +
                        "  ELSE 'unknown' " +
                        "END AS source, COUNT(*) AS count " +
                        "FROM biz_job_posting GROUP BY source ORDER BY count DESC"
        );
        report.put("sourceDistribution", sourceDistribution);

        Long historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_job_history", Long.class);
        report.put("jobHistorySnapshots", historyCount);

        Map<String, Object> governance = new HashMap<>();
        governance.put("dedupeRule", "url_obj_id");
        governance.put("historyTable", "biz_job_history");
        governance.put("latestSnapshotAt", LocalDateTime.now());
        report.put("governance", governance);

        return report;
    }

    public int backfillJobHistorySnapshots(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<JobPosting> jobs = jobMapper.selectList(null);
        int inserted = 0;
        for (JobPosting job : jobs) {
            if (inserted >= safeLimit) {
                break;
            }
            Long exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_job_history WHERE job_id = ?",
                    Long.class,
                    job.getId()
            );
            if (exists != null && exists > 0) {
                continue;
            }

            JobHistory history = new JobHistory();
            history.setJobId(job.getId());
            history.setSalaryMin(job.getSalaryMin());
            history.setSalaryMax(job.getSalaryMax());
            history.setIsActive(1);
            history.setChangeType("NEW");
            history.setCrawlTime(job.getCrawlTime() != null ? job.getCrawlTime() : LocalDateTime.now());
            history.setSnapshotData(toSnapshotJson(job));
            history.setCreatedAt(LocalDateTime.now());
            jobHistoryMapper.insert(history);
            inserted++;
        }
        log.info("Backfilled {} job history snapshots", inserted);
        return inserted;
    }

    private String toSnapshotJson(JobPosting job) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", job.getTitle());
        snapshot.put("companyName", job.getCompanyName());
        snapshot.put("city", job.getCity());
        snapshot.put("industryName", job.getIndustryName());
        snapshot.put("education", job.getEducation());
        snapshot.put("experience", job.getExperience());
        snapshot.put("salaryMin", job.getSalaryMin());
        snapshot.put("salaryMax", job.getSalaryMax());
        snapshot.put("salaryText", job.getSalaryText());
        snapshot.put("sourceUrl", job.getSourceUrl());
        snapshot.put("sourceSite", inferSourceSite(job.getSourceUrl()));
        snapshot.put("publishDate", job.getPublishDate());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String calcNonNullRate(String column) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_job_posting", Long.class);
        if (total == null || total == 0) {
            return "0%";
        }
        Long nonNull = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM biz_job_posting WHERE " + column + " IS NOT NULL AND TRIM(" + column + ") != ''",
                Long.class
        );
        return String.format("%.1f%%", (nonNull != null ? nonNull : 0) * 100.0 / total);
    }

    private String calcFieldRate(String condition) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_job_posting", Long.class);
        if (total == null || total == 0) {
            return "0%";
        }
        Long matching = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM biz_job_posting WHERE " + condition,
                Long.class
        );
        return String.format("%.1f%%", (matching != null ? matching : 0) * 100.0 / total);
    }

    private String inferSourceSite(String url) {
        if (url == null) {
            return "unknown";
        }
        String lower = url.toLowerCase();
        if (lower.contains("zhaopin")) {
            return "zhaopin";
        }
        if (lower.contains("51job")) {
            return "51job";
        }
        if (lower.contains("zhipin")) {
            return "boss";
        }
        return "unknown";
    }
}
