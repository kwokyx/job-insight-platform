package com.career.platform.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final JdbcTemplate jdbc;

    @Transactional
    public void runFullEtl() {
        log.info(">>> 数据仓库 全量ETL 开始 <<<");
        long start = System.currentTimeMillis();

        etlOdsToDwd();
        etlDwdToDwsCity();
        etlDwdToDwsIndustry();
        etlAdsKpi();

        log.info(">>> 数据仓库 全量ETL 完成，耗时 {} ms <<<", System.currentTimeMillis() - start);
    }

    /**
     * 增量 ETL — 只处理上次 ETL 时间之后的新增/变更记录
     * 定时任务：每小时执行。全量 ETL 每天凌晨执行。
     */
    @Transactional
    public void runIncrementalEtl() {
        log.info(">>> 数据仓库 增量ETL 开始 <<<");
        long start = System.currentTimeMillis();

        // 从 ADS 层读取上次 ETL 时间
        java.time.LocalDateTime lastEtlTime = getLastEtlTime();
        log.info("[增量ETL] 上次 ETL 时间: {}", lastEtlTime);

        etlOdsToDwdIncremental(lastEtlTime);
        // 维度聚合使用幂等 UPSERT，全量重刷保证数据正确性
        etlDwdToDwsCity();
        etlDwdToDwsIndustry();
        etlAdsKpi();

        log.info(">>> 数据仓库 增量ETL 完成，耗时 {} ms <<<", System.currentTimeMillis() - start);
    }

    /**
     * 读取上次 ETL 时间，不存在时返回 7 天前（保证首次运行也能覆盖近期数据）
     */
    private java.time.LocalDateTime getLastEtlTime() {
        try {
            String sql = "SELECT MAX(etl_time) AS last_time FROM ads_dashboard_kpi";
            java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(sql);
            if (!rows.isEmpty() && rows.get(0).get("last_time") != null) {
                Object raw = rows.get(0).get("last_time");
                if (raw instanceof java.time.LocalDateTime) {
                    return (java.time.LocalDateTime) raw;
                }
                // 兼容 Timestamp 类型
                return ((java.sql.Timestamp) raw).toLocalDateTime();
            }
        } catch (Exception e) {
            log.warn("[增量ETL] 无法读取上次 ETL 时间，使用 7 天前作为默认值: {}", e.getMessage());
        }
        return java.time.LocalDateTime.now().minusDays(7);
    }


    private void etlOdsToDwd() {
        log.info("[ETL] ODS -> DWD 开始");
        jdbc.execute(
                "INSERT INTO dwd_job_fact (" +
                        "job_id, title, company_name, city_std, province, industry_std, education_std, " +
                        "salary_min, salary_max, salary_avg, publish_date, source_site, etl_time" +
                        ") " +
                "SELECT " +
                        "j.id, j.title, j.company_name, COALESCE(j.city, j.job_city), COALESCE(j.region, j.province_code), COALESCE(j.industry_name, j.job_classification), COALESCE(j.education, j.education_need), " +
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
        log.info("[ETL] ODS -> DWD 完成");
    }

    /**
     * 增量 ODS -> DWD：只处理 updated_at > lastEtlTime 的记录
     */
    private void etlOdsToDwdIncremental(java.time.LocalDateTime lastEtlTime) {
        log.info("[增量ETL] ODS -> DWD 开始，起点时间: {}", lastEtlTime);
        jdbc.update(
                "INSERT INTO dwd_job_fact (" +
                        "job_id, title, company_name, city_std, province, industry_std, education_std, " +
                        "salary_min, salary_max, salary_avg, publish_date, source_site, etl_time" +
                        ") " +
                "SELECT " +
                        "j.id, j.title, j.company_name, COALESCE(j.city, j.job_city), COALESCE(j.region, j.province_code), COALESCE(j.industry_name, j.job_classification), COALESCE(j.education, j.education_need), " +
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
        log.info("[增量ETL] ODS -> DWD 完成");
    }

    private void etlDwdToDwsCity() {
        log.info("[ETL] DWD -> DWS(日/城市) 开始");
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
        log.info("[ETL] DWD -> DWS(日/城市) 完成");
    }

    private void etlDwdToDwsIndustry() {
        log.info("[ETL] DWD -> DWS(月/行业) 开始");
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
        log.info("[ETL] DWD -> DWS(月/行业) 完成");
    }

    private void etlAdsKpi() {
        log.info("[ETL] ADS KPI 开始");
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
        log.info("[ETL] ADS KPI 完成");
    }
}
