package com.career.platform.warehouse.service;

import com.career.platform.snapshot.service.PageSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final JdbcTemplate jdbc;
    private final PageSnapshotService pageSnapshotService;

    public WarehouseService(JdbcTemplate jdbc, PageSnapshotService pageSnapshotService) {
        this.jdbc = jdbc;
        this.pageSnapshotService = pageSnapshotService;
    }

    @Transactional
    public void runFullEtl() {
        log.info("Full ETL started");
        long start = System.currentTimeMillis();

        syncCrawlToBusinessIncremental();
        etlOdsToDwd();
        etlDwdToDwsCity();
        etlDwdToDwsIndustry();
        etlAdsKpi();
        etlAdsEmploymentIndicator();

        log.info("Full ETL finished in {} ms", System.currentTimeMillis() - start);
    }

    @Transactional
    public void runIncrementalEtl() {
        log.info("Incremental ETL started");
        long start = System.currentTimeMillis();

        syncCrawlToBusinessIncremental();
        LocalDateTime lastEtlTime = getLastEtlTime();
        log.info("Last ETL time: {}", lastEtlTime);

        etlOdsToDwdIncremental(lastEtlTime);
        etlDwdToDwsCity();
        etlDwdToDwsIndustry();
        etlAdsKpi();
        etlAdsEmploymentIndicator();

        log.info("Incremental ETL finished in {} ms", System.currentTimeMillis() - start);
    }

    @Transactional
    public Map<String, Object> syncCrawlToBusinessIncremental() {
        log.info("Syncing crawl tables into business tables");

        Integer companyAffectedRows = jdbc.update(
                "INSERT INTO biz_company (" +
                        "company_name, company_size, company_finance, logo_url, created_at, updated_at" +
                        ") " +
                        "SELECT src.company_name, src.company_size, src.company_finance, src.logo_url, NOW(), NOW() " +
                        "FROM (" +
                        "  SELECT company_name, " +
                        "         NULLIF(company_size, '') AS company_size, " +
                        "         NULLIF(company_finance, '') AS company_finance, " +
                        "         NULLIF(company_logo, '') AS logo_url, " +
                        "         ROW_NUMBER() OVER (PARTITION BY company_name ORDER BY crawl_time DESC, url_obj_id DESC) AS rn " +
                        "  FROM crawl_job_posting " +
                        "  WHERE company_name IS NOT NULL AND TRIM(company_name) <> ''" +
                        ") src " +
                        "WHERE src.rn = 1 " +
                        "ON DUPLICATE KEY UPDATE " +
                        "company_size = VALUES(company_size), " +
                        "company_finance = VALUES(company_finance), " +
                        "logo_url = VALUES(logo_url), " +
                        "updated_at = NOW()"
        );

        Integer jobAffectedRows = jdbc.update(
                "INSERT INTO biz_job_posting (" +
                        "url_obj_id, title, company_name, company_size, company_finance, " +
                        "region_code, province_code, city_code, job_city, job_classification, job_category_id, " +
                        "education_need, experience_year, salary_min, salary_max, salary_raw, job_welfare, " +
                        "job_labels, position_info, url, publish_date, crawl_time, crawl_update_time" +
                        ") " +
                        "SELECT src.url_obj_id, src.title, src.company_name, src.company_size, src.company_finance, " +
                        "NULL, NULL, NULL, src.job_city, src.job_classification, NULL, " +
                        "src.education_need, src.experience_year, " +
                        "CASE WHEN src.salary_min IS NULL OR src.salary_min <= 0 THEN NULL " +
                        "     WHEN src.salary_min > 200 THEN ROUND(src.salary_min / 1000, 2) " +
                        "     ELSE src.salary_min END, " +
                        "CASE WHEN src.salary_max IS NULL OR src.salary_max <= 0 THEN NULL " +
                        "     WHEN src.salary_max > 200 THEN ROUND(src.salary_max / 1000, 2) " +
                        "     ELSE src.salary_max END, " +
                        "src.salary_raw, src.job_welfare, " +
                        "CASE WHEN src.job_labels IS NULL OR TRIM(src.job_labels) = '' THEN NULL " +
                        "     ELSE JSON_ARRAY(TRIM(BOTH '\"' FROM REPLACE(REPLACE(src.job_labels, '\\\\', '\\\\\\\\'), '\"', '\\\\\"'))) END, " +
                        "src.position_info, src.url, DATE(src.publish_date), src.crawl_time, src.crawl_update_time " +
                        "FROM crawl_job_posting src " +
                        "ON DUPLICATE KEY UPDATE " +
                        "title = VALUES(title), " +
                        "company_name = VALUES(company_name), " +
                        "company_size = VALUES(company_size), " +
                        "company_finance = VALUES(company_finance), " +
                        "job_city = VALUES(job_city), " +
                        "job_classification = VALUES(job_classification), " +
                        "education_need = VALUES(education_need), " +
                        "experience_year = VALUES(experience_year), " +
                        "salary_min = VALUES(salary_min), " +
                        "salary_max = VALUES(salary_max), " +
                        "salary_raw = VALUES(salary_raw), " +
                        "job_welfare = VALUES(job_welfare), " +
                        "job_labels = VALUES(job_labels), " +
                        "position_info = VALUES(position_info), " +
                        "url = VALUES(url), " +
                        "publish_date = VALUES(publish_date), " +
                        "crawl_time = VALUES(crawl_time), " +
                        "crawl_update_time = VALUES(crawl_update_time)"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyAffectedRows", companyAffectedRows);
        result.put("jobAffectedRows", jobAffectedRows);
        result.put("crawlRows", safeCount("SELECT COUNT(*) FROM crawl_job_posting"));
        result.put("bizRows", safeCount("SELECT COUNT(*) FROM biz_job_posting"));
        return result;
    }

    public Map<String, Object> refreshPageSnapshots(List<String> pageCodes, String trigger) {
        return pageSnapshotService.refreshSnapshots(pageCodes, trigger);
    }

    public Map<String, Object> snapshotStatus() {
        return pageSnapshotService.snapshotStatus();
    }

    public Map<String, Object> crawlBusinessCounts() {
        Map<String, Object> result = new LinkedHashMap<>();
        Long crawlRows = safeCount("SELECT COUNT(*) FROM crawl_job_posting");
        Long bizRows = safeCount("SELECT COUNT(*) FROM biz_job_posting");
        Long todayIncrement = safeCount("SELECT COUNT(*) FROM biz_job_posting WHERE DATE(crawl_time) = CURDATE()");
        String latestSyncAt = safeScalar(
                "SELECT DATE_FORMAT(MAX(COALESCE(crawl_update_time, crawl_time)), '%Y-%m-%d %H:%i:%s') FROM biz_job_posting",
                String.class
        );
        Long latestNewCount = safeCount("SELECT COALESCE(SUM(new_count),0) FROM crawl_task WHERE DATE(update_time) = CURDATE()");
        Long latestUpdatedCount = safeCount("SELECT COALESCE(SUM(updated_count),0) FROM crawl_task WHERE DATE(update_time) = CURDATE()");
        Long latestDuplicateCount = safeCount("SELECT COALESCE(SUM(duplicate_count),0) FROM crawl_task WHERE DATE(update_time) = CURDATE()");

        result.put("crawlRows", crawlRows);
        result.put("bizRows", bizRows);
        result.put("totalJobs", bizRows);
        result.put("todayIncrement", todayIncrement);
        result.put("latestSyncAt", latestSyncAt);
        result.put("latestNewCount", latestNewCount);
        result.put("latestUpdatedCount", latestUpdatedCount);
        result.put("latestDuplicateCount", latestDuplicateCount);
        return result;
    }

    public Map<String, Object> crawlRealtimeSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("crawlRows", safeCount("SELECT COUNT(*) FROM crawl_job_posting"));
        result.put("bizRows", safeCount("SELECT COUNT(*) FROM biz_job_posting"));
        result.put("todayRows", safeCount("SELECT COUNT(*) FROM crawl_job_posting WHERE DATE(crawl_time) = CURDATE()"));
        result.put("todayBizRows", safeCount("SELECT COUNT(*) FROM biz_job_posting WHERE DATE(crawl_time) = CURDATE()"));
        result.put("newJobs7d", safeCount("SELECT COUNT(*) FROM biz_job_posting WHERE publish_date >= CURDATE() - INTERVAL 7 DAY"));
        result.put("activeCompanies", safeCount("SELECT COUNT(DISTINCT company_name) FROM biz_job_posting WHERE company_name IS NOT NULL AND TRIM(company_name) <> ''"));
        result.put("latestCrawlTime", safeScalar(
                "SELECT DATE_FORMAT(MAX(crawl_time), '%Y-%m-%d %H:%i:%s') FROM crawl_job_posting",
                String.class
        ));
        result.put("latestBizTime", safeScalar(
                "SELECT DATE_FORMAT(MAX(crawl_time), '%Y-%m-%d %H:%i:%s') FROM biz_job_posting",
                String.class
        ));
        result.put("recentJobs", jdbc.query(
                "SELECT title, company_name, COALESCE(job_city, city, region_code, province_code) AS city, " +
                        "salary_raw, DATE_FORMAT(crawl_time, '%Y-%m-%d %H:%i:%s') AS crawl_time, url " +
                        "FROM biz_job_posting " +
                        "ORDER BY crawl_time DESC, id DESC LIMIT 12",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", rs.getString("title"));
                    row.put("companyName", rs.getString("company_name"));
                    row.put("city", rs.getString("city"));
                    row.put("salaryRaw", rs.getString("salary_raw"));
                    row.put("crawlTime", rs.getString("crawl_time"));
                    row.put("url", rs.getString("url"));
                    return row;
                }
        ));
        result.put("topSources", queryTopSources());
        return result;
    }

    private List<Map<String, Object>> queryTopSources() {
        try {
            if (hasColumn("crawl_job_posting", "source")) {
                return jdbc.queryForList(
                        "SELECT source, COUNT(*) AS count FROM crawl_job_posting " +
                                "WHERE source IS NOT NULL AND TRIM(source) <> '' " +
                                "GROUP BY source ORDER BY count DESC LIMIT 5"
                );
            }
            if (hasColumn("biz_job_posting", "source_site")) {
                return jdbc.queryForList(
                        "SELECT source_site AS source, COUNT(*) AS count FROM biz_job_posting " +
                                "WHERE source_site IS NOT NULL AND TRIM(source_site) <> '' " +
                                "GROUP BY source_site ORDER BY count DESC LIMIT 5"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to query crawl top sources: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class,
                    tableName,
                    columnName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to inspect column {}.{}: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    private LocalDateTime getLastEtlTime() {
        try {
            String sql = "SELECT MAX(etl_time) AS last_time FROM ads_dashboard_kpi";
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            if (!rows.isEmpty() && rows.get(0).get("last_time") != null) {
                Object raw = rows.get(0).get("last_time");
                if (raw instanceof LocalDateTime) {
                    return (LocalDateTime) raw;
                }
                if (raw instanceof Timestamp) {
                    return ((Timestamp) raw).toLocalDateTime();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read last ETL time, fallback to 7 days ago: {}", e.getMessage());
        }
        return LocalDateTime.now().minusDays(7);
    }

    private void etlOdsToDwd() {
        log.info("ODS -> DWD started");
        jdbc.execute(
                "INSERT INTO dwd_job_fact (" +
                        "job_id, title, company_name, city_std, province, industry_std, education_std, " +
                        "salary_min, salary_max, salary_avg, publish_date, source_site, etl_time" +
                        ") " +
                        "SELECT " +
                        "j.id, j.title, j.company_name, COALESCE(j.city, j.job_city), COALESCE(j.region, j.province_code), " +
                        "COALESCE(j.industry_name, j.job_classification), COALESCE(j.education, j.education_need), " +
                        "j.salary_min, j.salary_max, ROUND((IFNULL(j.salary_min, 0) + IFNULL(j.salary_max, 0)) / 2, 2), " +
                        "j.publish_date, " +
                        "COALESCE(j.source_site, CASE " +
                        "  WHEN COALESCE(j.source_url, j.url, '') LIKE '%zhaopin%' THEN 'zhaopin' " +
                        "  WHEN COALESCE(j.source_url, j.url, '') LIKE '%51job%' THEN '51job' " +
                        "  WHEN COALESCE(j.source_url, j.url, '') LIKE '%zhipin%' THEN 'boss' " +
                        "  ELSE 'unknown' " +
                        "END), " +
                        "NOW() " +
                        "FROM biz_job_posting j " +
                        "WHERE j.id IS NOT NULL " +
                        "ON DUPLICATE KEY UPDATE " +
                        "title = VALUES(title), " +
                        "company_name = VALUES(company_name), " +
                        "city_std = VALUES(city_std), " +
                        "province = VALUES(province), " +
                        "industry_std = VALUES(industry_std), " +
                        "education_std = VALUES(education_std), " +
                        "salary_min = VALUES(salary_min), " +
                        "salary_max = VALUES(salary_max), " +
                        "salary_avg = VALUES(salary_avg), " +
                        "publish_date = VALUES(publish_date), " +
                        "source_site = VALUES(source_site), " +
                        "etl_time = NOW()"
        );
        log.info("ODS -> DWD finished");
    }

    private void etlOdsToDwdIncremental(LocalDateTime lastEtlTime) {
        log.info("Incremental ODS -> DWD started from {}", lastEtlTime);
        jdbc.update(
                "INSERT INTO dwd_job_fact (" +
                        "job_id, title, company_name, city_std, province, industry_std, education_std, " +
                        "salary_min, salary_max, salary_avg, publish_date, source_site, etl_time" +
                        ") " +
                        "SELECT " +
                        "j.id, j.title, j.company_name, COALESCE(j.city, j.job_city), COALESCE(j.region, j.province_code), " +
                        "COALESCE(j.industry_name, j.job_classification), COALESCE(j.education, j.education_need), " +
                        "j.salary_min, j.salary_max, ROUND((IFNULL(j.salary_min, 0) + IFNULL(j.salary_max, 0)) / 2, 2), " +
                        "j.publish_date, " +
                        "COALESCE(j.source_site, CASE " +
                        "  WHEN COALESCE(j.source_url, j.url, '') LIKE '%zhaopin%' THEN 'zhaopin' " +
                        "  WHEN COALESCE(j.source_url, j.url, '') LIKE '%51job%' THEN '51job' " +
                        "  WHEN COALESCE(j.source_url, j.url, '') LIKE '%zhipin%' THEN 'boss' " +
                        "  ELSE 'unknown' " +
                        "END), " +
                        "NOW() " +
                        "FROM biz_job_posting j " +
                        "WHERE j.id IS NOT NULL AND j.updated_at > ? " +
                        "ON DUPLICATE KEY UPDATE " +
                        "title = VALUES(title), " +
                        "company_name = VALUES(company_name), " +
                        "city_std = VALUES(city_std), " +
                        "province = VALUES(province), " +
                        "industry_std = VALUES(industry_std), " +
                        "education_std = VALUES(education_std), " +
                        "salary_min = VALUES(salary_min), " +
                        "salary_max = VALUES(salary_max), " +
                        "salary_avg = VALUES(salary_avg), " +
                        "publish_date = VALUES(publish_date), " +
                        "source_site = VALUES(source_site), " +
                        "etl_time = NOW()",
                lastEtlTime
        );
        log.info("Incremental ODS -> DWD finished");
    }

    private void etlDwdToDwsCity() {
        log.info("DWD -> DWS city started");
        jdbc.execute(
                "INSERT INTO dws_daily_city_summary (" +
                        "stat_date, city, job_count, avg_salary_min, avg_salary_max, new_jobs_count, etl_time" +
                        ") " +
                        "SELECT " +
                        "publish_date, city_std, COUNT(*), ROUND(AVG(salary_min), 2), ROUND(AVG(salary_max), 2), COUNT(*), NOW() " +
                        "FROM dwd_job_fact " +
                        "WHERE publish_date IS NOT NULL AND city_std IS NOT NULL AND city_std != '' " +
                        "GROUP BY publish_date, city_std " +
                        "ON DUPLICATE KEY UPDATE " +
                        "job_count = VALUES(job_count), " +
                        "avg_salary_min = VALUES(avg_salary_min), " +
                        "avg_salary_max = VALUES(avg_salary_max), " +
                        "new_jobs_count = VALUES(new_jobs_count), " +
                        "etl_time = NOW()"
        );
        log.info("DWD -> DWS city finished");
    }

    private void etlDwdToDwsIndustry() {
        log.info("DWD -> DWS industry started");
        jdbc.execute(
                "INSERT INTO dws_monthly_industry_summary (" +
                        "stat_month, industry, job_count, avg_salary_min, avg_salary_max, etl_time" +
                        ") " +
                        "SELECT " +
                        "DATE_FORMAT(publish_date, '%Y-%m'), industry_std, COUNT(*), ROUND(AVG(salary_min), 2), ROUND(AVG(salary_max), 2), NOW() " +
                        "FROM dwd_job_fact " +
                        "WHERE publish_date IS NOT NULL AND industry_std IS NOT NULL AND industry_std != '' " +
                        "GROUP BY DATE_FORMAT(publish_date, '%Y-%m'), industry_std " +
                        "ON DUPLICATE KEY UPDATE " +
                        "job_count = VALUES(job_count), " +
                        "avg_salary_min = VALUES(avg_salary_min), " +
                        "avg_salary_max = VALUES(avg_salary_max), " +
                        "etl_time = NOW()"
        );
        log.info("DWD -> DWS industry finished");
    }

    private void etlAdsKpi() {
        log.info("ADS KPI started");
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        jdbc.update(
                "INSERT INTO ads_dashboard_kpi (" +
                        "stat_date, total_jobs, active_jobs, total_companies, avg_salary, top_city, top_industry, top_skill, new_jobs_7d, salary_trend, etl_time" +
                        ") " +
                        "SELECT " +
                        "?, " +
                        "(SELECT COUNT(*) FROM biz_job_posting), " +
                        "(SELECT COUNT(*) FROM biz_job_posting WHERE is_active = 1), " +
                        "(SELECT COUNT(DISTINCT company_name) FROM biz_job_posting), " +
                        "(SELECT ROUND(AVG((IFNULL(salary_min, 0) + IFNULL(salary_max, 0)) / 2), 2) FROM biz_job_posting WHERE salary_min > 0), " +
                        "(SELECT city_std FROM dwd_job_fact WHERE city_std IS NOT NULL GROUP BY city_std ORDER BY COUNT(*) DESC LIMIT 1), " +
                        "(SELECT industry_std FROM dwd_job_fact WHERE industry_std IS NOT NULL GROUP BY industry_std ORDER BY COUNT(*) DESC LIMIT 1), " +
                        "(SELECT d.label_name FROM job_label_rel r JOIN job_label_dict d ON r.label_id = d.id GROUP BY d.id, d.label_name ORDER BY COUNT(*) DESC LIMIT 1), " +
                        "(SELECT COUNT(*) FROM biz_job_posting WHERE publish_date >= CURDATE() - INTERVAL 7 DAY), " +
                        "'FLAT', NOW() " +
                        "ON DUPLICATE KEY UPDATE " +
                        "total_jobs = VALUES(total_jobs), " +
                        "active_jobs = VALUES(active_jobs), " +
                        "total_companies = VALUES(total_companies), " +
                        "avg_salary = VALUES(avg_salary), " +
                        "top_city = VALUES(top_city), " +
                        "top_industry = VALUES(top_industry), " +
                        "top_skill = VALUES(top_skill), " +
                        "new_jobs_7d = VALUES(new_jobs_7d), " +
                        "salary_trend = VALUES(salary_trend), " +
                        "etl_time = NOW()",
                today
        );
        log.info("ADS KPI finished");
    }

    private void etlAdsEmploymentIndicator() {
        log.info("Employment indicator started");
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        boolean hasNewSchema = tableHasColumn("biz_employment_indicator", "stat_date")
                && tableHasColumn("biz_employment_indicator", "dimension_type")
                && tableHasColumn("biz_employment_indicator", "dimension_value")
                && tableHasColumn("biz_employment_indicator", "job_count")
                && tableHasColumn("biz_employment_indicator", "recruit_demand_score");
        if (hasNewSchema) {
            jdbc.update(
                    "INSERT INTO biz_employment_indicator (" +
                            "stat_date, dimension_type, dimension_value, " +
                            "job_count, avg_salary_min, avg_salary_max, " +
                            "recruit_demand_score, etl_time" +
                            ") " +
                            "SELECT ?, 'CITY', city_std, COUNT(*), " +
                            "ROUND(AVG(salary_min), 2), ROUND(AVG(salary_max), 2), " +
                            "ROUND(COUNT(*) / 100.0, 2), NOW() " +
                            "FROM dwd_job_fact " +
                            "WHERE city_std IS NOT NULL AND city_std != '' " +
                            "GROUP BY city_std " +
                            "ON DUPLICATE KEY UPDATE " +
                            "job_count = VALUES(job_count), " +
                            "avg_salary_min = VALUES(avg_salary_min), " +
                            "avg_salary_max = VALUES(avg_salary_max), " +
                            "recruit_demand_score = VALUES(recruit_demand_score), " +
                            "etl_time = NOW()",
                    today
            );
        } else {
            // Backward-compatible write path for legacy biz_employment_indicator schema.
            jdbc.update(
                    "DELETE FROM biz_employment_indicator WHERE indicator_code = 'EMP_CITY_DEMAND' AND period = ?",
                    today
            );
            jdbc.update(
                    "INSERT INTO biz_employment_indicator (" +
                            "indicator_name, indicator_code, metric_scope, period, region, value, unit, source_site, publish_date, created_at" +
                            ") " +
                            "SELECT CONCAT(city_std, '招聘需求指数'), 'EMP_CITY_DEMAND', 'CITY', ?, city_std, " +
                            "ROUND(COUNT(*) / 100.0, 2), 'score', 'zhaopin', CURDATE(), NOW() " +
                            "FROM dwd_job_fact " +
                            "WHERE city_std IS NOT NULL AND city_std != '' " +
                            "GROUP BY city_std",
                    today
            );
        }
        log.info("Employment indicator finished");
    }

    private boolean tableHasColumn(String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private Long safeCount(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("Count query failed: {}", e.getMessage());
            return 0L;
        }
    }

    private <T> T safeScalar(String sql, Class<T> clazz) {
        try {
            return jdbc.queryForObject(sql, clazz);
        } catch (Exception e) {
            log.warn("Scalar query failed: {}", e.getMessage());
            return null;
        }
    }
}
