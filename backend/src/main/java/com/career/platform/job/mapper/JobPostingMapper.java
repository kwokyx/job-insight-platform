package com.career.platform.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.career.platform.job.entity.JobPosting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface JobPostingMapper extends BaseMapper<JobPosting> {

    @Select("SELECT job_city AS city, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE job_city IS NOT NULL AND job_city != '' " +
            "GROUP BY job_city ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> aggregateByCity(int limit);

    @Select("SELECT job_classification AS industry, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE job_classification IS NOT NULL AND job_classification != '' " +
            "GROUP BY job_classification ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> aggregateByIndustry(int limit);

    @Select("SELECT education_need AS education, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE education_need IS NOT NULL AND education_need != '' " +
            "GROUP BY education_need ORDER BY count DESC")
    List<Map<String, Object>> aggregateByEducation();

    @Select("SELECT experience_year AS experience, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE experience_year IS NOT NULL AND experience_year != '' " +
            "GROUP BY experience_year ORDER BY count DESC")
    List<Map<String, Object>> aggregateByExperience();

    @Select("SELECT d.label_name AS skill, COUNT(*) AS count " +
            "FROM job_label_rel r " +
            "JOIN job_label_dict d ON r.label_id = d.id " +
            "WHERE d.label_type IN ('skill', 'tool', 'language', 'framework') " +
            "GROUP BY d.id, d.label_name ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> topSkills(int limit);

    @Select("SELECT COUNT(*) AS totalJobs, " +
            "ROUND(AVG(salary_min),2) AS avgSalaryMin, " +
            "ROUND(AVG(salary_max),2) AS avgSalaryMax " +
            "FROM biz_job_posting WHERE salary_min > 0")
    Map<String, Object> overviewStats();

    @Select("<script>" +
            "SELECT " +
            "  jp.id, " +
            "  jp.title, " +
            "  jp.company_name AS companyName, " +
            "  jp.job_city AS city, " +
            "  jp.job_classification AS industryName, " +
            "  jp.education_need AS education, " +
            "  jp.experience_year AS experience, " +
            "  jp.salary_min AS salaryMin, " +
            "  jp.salary_max AS salaryMax, " +
            "  jp.salary_raw AS salaryText, " +
            "  jp.publish_date AS publishDate " +
            "FROM biz_job_posting jp " +
            "WHERE jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "ORDER BY jp.publish_date DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Map<String, Object>> searchJobs(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("<script>" +
            "SELECT COUNT(*) FROM biz_job_posting jp " +
            "WHERE jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "</script>")
    long countSearchJobs(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT job_city AS city, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE job_city IS NOT NULL AND job_city != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY job_city " +
            "ORDER BY count DESC " +
            "LIMIT 10" +
            "</script>")
    List<Map<String, Object>> searchAggregateByCity(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT job_classification AS industry, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE job_classification IS NOT NULL AND job_classification != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY job_classification " +
            "ORDER BY count DESC " +
            "LIMIT 10" +
            "</script>")
    List<Map<String, Object>> searchAggregateByIndustry(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT education_need AS education, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE education_need IS NOT NULL AND education_need != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY education_need " +
            "ORDER BY count DESC" +
            "</script>")
    List<Map<String, Object>> searchAggregateByEducation(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT experience_year AS experience, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE experience_year IS NOT NULL AND experience_year != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY experience_year " +
            "ORDER BY count DESC" +
            "</script>")
    List<Map<String, Object>> searchAggregateByExperience(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("SELECT id, title, company_name AS companyName, job_city AS city, job_classification AS industryName, " +
            "salary_min AS salaryMin, salary_max AS salaryMax, salary_raw AS salaryText, " +
            "publish_date AS publishDate " +
            "FROM biz_job_posting " +
            "ORDER BY publish_date DESC, salary_max DESC, salary_min DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> hotJobs(@Param("limit") int limit);

    @Select("<script>" +
            "SELECT DATE_FORMAT(publish_date, '%Y-%m') AS period, " +
            "  ROUND(AVG(salary_min), 2) AS avgSalaryMin, " +
            "  ROUND(AVG(salary_max), 2) AS avgSalaryMax, " +
            "  COUNT(*) AS jobCount " +
            "FROM biz_job_posting " +
            "WHERE 1 = 1 " +
            "  AND publish_date IS NOT NULL " +
            "  AND salary_min IS NOT NULL " +
            "  <if test=\"city != null and city != ''\"> " +
            "    AND job_city LIKE CONCAT('%', #{city}, '%') " +
            "  </if> " +
            "  <if test=\"industry != null and industry != ''\"> " +
            "    AND job_classification LIKE CONCAT('%', #{industry}, '%') " +
            "  </if> " +
            "GROUP BY DATE_FORMAT(publish_date, '%Y-%m') " +
            "ORDER BY period" +
            "</script>")
    List<Map<String, Object>> salaryTrend(
            @Param("city") String city,
            @Param("industry") String industry
    );

    @Select("SELECT d.label_name " +
            "FROM job_label_rel r " +
            "JOIN job_label_dict d ON r.label_id = d.id " +
            "WHERE r.job_posting_id = #{jobId} " +
            "  AND d.label_type IN ('skill', 'tool', 'language', 'framework') " +
            "ORDER BY d.label_name")
    List<String> jobSkills(@Param("jobId") Long jobId);

    @Select("<script>" +
            "SELECT " +
            "  jp.id, " +
            "  jp.title, " +
            "  jp.company_name AS companyName, " +
            "  jp.job_city AS city, " +
            "  jp.job_classification AS industryName, " +
            "  jp.salary_raw AS salaryText, " +
            "  jp.publish_date AS publishDate, " +
            "  COUNT(DISTINCT r.label_id) AS overlapSkills, " +
            "  GROUP_CONCAT(DISTINCT d.label_name ORDER BY d.label_name SEPARATOR ', ') AS matchedSkills " +
            "FROM biz_job_posting jp " +
            "JOIN job_label_rel r ON jp.id = r.job_posting_id " +
            "JOIN job_label_dict d ON r.label_id = d.id " +
            "WHERE jp.id != #{jobId} " +
            "  AND d.label_type IN ('skill', 'tool', 'language', 'framework') " +
            "  AND r.label_id IN ( " +
            "    SELECT r2.label_id FROM job_label_rel r2 " +
            "    JOIN job_label_dict d2 ON r2.label_id = d2.id " +
            "    WHERE r2.job_posting_id = #{jobId} " +
            "      AND d2.label_type IN ('skill', 'tool', 'language', 'framework') " +
            "  ) " +
            "  <if test=\"city != null and city != ''\"> " +
            "    AND jp.job_city LIKE CONCAT('%', #{city}, '%') " +
            "  </if> " +
            "GROUP BY jp.id, jp.title, jp.company_name, jp.job_city, jp.job_classification, jp.salary_raw, jp.publish_date " +
            "ORDER BY overlapSkills DESC, jp.publish_date DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> similarJobsBySkills(
            @Param("jobId") Long jobId,
            @Param("city") String city,
            @Param("limit") int limit
    );
}
