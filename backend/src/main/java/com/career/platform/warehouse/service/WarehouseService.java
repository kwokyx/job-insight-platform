package com.career.platform.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据仓库 ETL 服务
 * ODS (biz_job_posting) → DWD (dwd_job_fact) → DWS → ADS
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final JdbcTemplate jdbc;

    /**
     * 执行全量 ETL 流程
     */
    @Transactional
    public void runFullEtl() {
        log.info(">>> 数仓 ETL 开始 <<<");
        long start = System.currentTimeMillis();

        etlOdsToDwd();
        etlDwdToDwsCity();
        etlDwdToDwsIndustry();
        etlAdsKpi();

        long elapsed = System.currentTimeMillis() - start;
        log.info(">>> 数仓 ETL 完成，耗时 {}ms <<<", elapsed);
    }

    /**
     * ODS → DWD: 清洗标准化，写入明细宽表
     */
    private void etlOdsToDwd() {
        log.info("[ETL] ODS → DWD 开始...");
        jdbc.execute(
            "INSERT INTO dwd_job_fact (job_id, title, company_name, city_std, province, " +
            "    industry_std, education_std, salary_min, salary_max, salary_avg, " +
            "    publish_date, source_site, etl_time) " +
            "SELECT " +
            "    j.id, j.title, j.company_name, " +
            "    CASE " +
            "        WHEN j.job_city LIKE '%北京%' THEN '北京' " +
            "        WHEN j.job_city LIKE '%上海%' THEN '上海' " +
            "        WHEN j.job_city LIKE '%广州%' THEN '广州' " +
            "        WHEN j.job_city LIKE '%深圳%' THEN '深圳' " +
            "        WHEN j.job_city LIKE '%杭州%' THEN '杭州' " +
            "        WHEN j.job_city LIKE '%成都%' THEN '成都' " +
            "        WHEN j.job_city LIKE '%南京%' THEN '南京' " +
            "        WHEN j.job_city LIKE '%武汉%' THEN '武汉' " +
            "        WHEN j.job_city LIKE '%西安%' THEN '西安' " +
            "        WHEN j.job_city LIKE '%重庆%' THEN '重庆' " +
            "        WHEN j.job_city LIKE '%苏州%' THEN '苏州' " +
            "        WHEN j.job_city LIKE '%天津%' THEN '天津' " +
            "        WHEN j.job_city LIKE '%长沙%' THEN '长沙' " +
            "        WHEN j.job_city LIKE '%郑州%' THEN '郑州' " +
            "        WHEN j.job_city LIKE '%合肥%' THEN '合肥' " +
            "        ELSE j.job_city " +
            "    END, " +
            "    NULL, " +  // province 暂不提取
            "    j.job_classification, " +
            "    CASE " +
            "        WHEN j.education_need LIKE '%博士%' THEN '博士' " +
            "        WHEN j.education_need LIKE '%硕士%' OR j.education_need LIKE '%研究生%' THEN '硕士' " +
            "        WHEN j.education_need LIKE '%本科%' THEN '本科' " +
            "        WHEN j.education_need LIKE '%大专%' OR j.education_need LIKE '%专科%' THEN '大专' " +
            "        WHEN j.education_need LIKE '%高中%' OR j.education_need LIKE '%中专%' OR j.education_need LIKE '%中技%' THEN '高中及以下' " +
            "        ELSE j.education_need " +
            "    END, " +
            "    j.salary_min, j.salary_max, " +
            "    ROUND((IFNULL(j.salary_min, 0) + IFNULL(j.salary_max, 0)) / 2, 2), " +
            "    j.publish_date, CASE " +
            "        WHEN j.url LIKE '%zhaopin%' THEN 'zhaopin' " +
            "        WHEN j.url LIKE '%51job%' THEN '51job' " +
            "        WHEN j.url LIKE '%zhipin%' THEN 'boss' " +
            "        ELSE 'unknown' END, NOW() " +
            "FROM biz_job_posting j " +
            "ON DUPLICATE KEY UPDATE " +
            "    title = VALUES(title), company_name = VALUES(company_name), " +
            "    city_std = VALUES(city_std), industry_std = VALUES(industry_std), " +
            "    education_std = VALUES(education_std), salary_min = VALUES(salary_min), " +
            "    salary_max = VALUES(salary_max), salary_avg = VALUES(salary_avg), " +
            "    publish_date = VALUES(publish_date), etl_time = NOW()"
        );
        log.info("[ETL] ODS → DWD 完成");
    }

    /**
     * DWD → DWS: 按日按城市汇总
     */
    private void etlDwdToDwsCity() {
        log.info("[ETL] DWD → DWS (按日城市) 开始...");
        jdbc.execute(
            "INSERT INTO dws_daily_city_summary (stat_date, city, job_count, " +
            "    avg_salary_min, avg_salary_max, new_jobs_count, etl_time) " +
            "SELECT " +
            "    d.publish_date, d.city_std, COUNT(*), " +
            "    ROUND(AVG(d.salary_min), 2), ROUND(AVG(d.salary_max), 2), " +
            "    COUNT(*), NOW() " +
            "FROM dwd_job_fact d " +
            "WHERE d.publish_date IS NOT NULL AND d.city_std IS NOT NULL " +
            "GROUP BY d.publish_date, d.city_std " +
            "ON DUPLICATE KEY UPDATE " +
            "    job_count = VALUES(job_count), " +
            "    avg_salary_min = VALUES(avg_salary_min), " +
            "    avg_salary_max = VALUES(avg_salary_max), " +
            "    new_jobs_count = VALUES(new_jobs_count), " +
            "    etl_time = NOW()"
        );
        log.info("[ETL] DWD → DWS (按日城市) 完成");
    }

    /**
     * DWD → DWS: 按月按行业汇总
     */
    private void etlDwdToDwsIndustry() {
        log.info("[ETL] DWD → DWS (按月行业) 开始...");
        jdbc.execute(
            "INSERT INTO dws_monthly_industry_summary (stat_month, industry, job_count, " +
            "    avg_salary_min, avg_salary_max, etl_time) " +
            "SELECT " +
            "    DATE_FORMAT(d.publish_date, '%Y-%m'), d.industry_std, COUNT(*), " +
            "    ROUND(AVG(d.salary_min), 2), ROUND(AVG(d.salary_max), 2), NOW() " +
            "FROM dwd_job_fact d " +
            "WHERE d.publish_date IS NOT NULL AND d.industry_std IS NOT NULL " +
            "GROUP BY DATE_FORMAT(d.publish_date, '%Y-%m'), d.industry_std " +
            "ON DUPLICATE KEY UPDATE " +
            "    job_count = VALUES(job_count), " +
            "    avg_salary_min = VALUES(avg_salary_min), " +
            "    avg_salary_max = VALUES(avg_salary_max), " +
            "    etl_time = NOW()"
        );
        log.info("[ETL] DWD → DWS (按月行业) 完成");
    }

    /**
     * DWS → ADS: 看板 KPI 快照
     */
    private void etlAdsKpi() {
        log.info("[ETL] ADS KPI 计算开始...");
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        jdbc.update(
            "INSERT INTO ads_dashboard_kpi (stat_date, total_jobs, active_jobs, total_companies, " +
            "    avg_salary, top_city, top_industry, top_skill, new_jobs_7d, salary_trend, etl_time) " +
            "SELECT " +
            "    ?, " +
            "    (SELECT COUNT(*) FROM biz_job_posting), " +
            "    (SELECT COUNT(*) FROM biz_job_posting), " +
            "    (SELECT COUNT(DISTINCT company_name) FROM biz_job_posting), " +
            "    (SELECT ROUND(AVG((IFNULL(salary_min,0)+IFNULL(salary_max,0))/2), 2) FROM biz_job_posting WHERE salary_min > 0), " +
            "    (SELECT city_std FROM dwd_job_fact GROUP BY city_std ORDER BY COUNT(*) DESC LIMIT 1), " +
            "    (SELECT industry_std FROM dwd_job_fact WHERE industry_std IS NOT NULL GROUP BY industry_std ORDER BY COUNT(*) DESC LIMIT 1), " +
            "    (SELECT d.label_name " +
            "       FROM job_label_rel r " +
            "       JOIN job_label_dict d ON r.label_id = d.id " +
            "      WHERE d.label_type IN ('skill', 'tool', 'language', 'framework') " +
            "      GROUP BY d.id, d.label_name ORDER BY COUNT(*) DESC LIMIT 1), " +
            "    (SELECT COUNT(*) FROM biz_job_posting WHERE publish_date >= CURDATE() - INTERVAL 7 DAY), " +
            "    'FLAT', NOW() " +
            "ON DUPLICATE KEY UPDATE " +
            "    total_jobs = VALUES(total_jobs), active_jobs = VALUES(active_jobs), " +
            "    total_companies = VALUES(total_companies), avg_salary = VALUES(avg_salary), " +
            "    top_city = VALUES(top_city), top_industry = VALUES(top_industry), " +
            "    top_skill = VALUES(top_skill), new_jobs_7d = VALUES(new_jobs_7d), etl_time = NOW()",
            today
        );
        log.info("[ETL] ADS KPI 计算完成");
    }
}
