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

    @Select("SELECT city, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE city IS NOT NULL AND city != '' " +
            "GROUP BY city ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> aggregateByCity(int limit);

    @Select("SELECT industry_name AS industry, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE industry_name IS NOT NULL AND industry_name != '' " +
            "GROUP BY industry_name ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> aggregateByIndustry(int limit);

    @Select("SELECT education, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE education IS NOT NULL AND education != '' " +
            "GROUP BY education ORDER BY count DESC")
    List<Map<String, Object>> aggregateByEducation();

    @Select("SELECT experience, COUNT(*) AS count, ROUND(AVG(salary_min),2) AS avgSalary " +
            "FROM biz_job_posting WHERE experience IS NOT NULL AND experience != '' " +
            "GROUP BY experience ORDER BY count DESC")
    List<Map<String, Object>> aggregateByExperience();

    @Select("SELECT s.skill_name AS skill, COUNT(*) AS count " +
            "FROM biz_job_skill js JOIN biz_skill s ON js.skill_id = s.id " +
            "GROUP BY s.skill_name ORDER BY count DESC LIMIT #{limit}")
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
            "  jp.city, " +
            "  jp.industry_name AS industryName, " +
            "  jp.education, " +
            "  jp.experience, " +
            "  jp.salary_min AS salaryMin, " +
            "  jp.salary_max AS salaryMax, " +
            "  jp.salary_text AS salaryText, " +
            "  jp.publish_date AS publishDate " +
            "FROM biz_job_posting jp " +
            "WHERE jp.is_active = 1 " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
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
            "WHERE jp.is_active = 1 " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "</script>")
    long countSearchJobs(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT city, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE jp.is_active = 1 " +
            "  AND city IS NOT NULL AND city != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY city " +
            "ORDER BY count DESC " +
            "LIMIT 10" +
            "</script>")
    List<Map<String, Object>> searchAggregateByCity(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT industry_name AS industry, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE jp.is_active = 1 " +
            "  AND industry_name IS NOT NULL AND industry_name != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY industry_name " +
            "ORDER BY count DESC " +
            "LIMIT 10" +
            "</script>")
    List<Map<String, Object>> searchAggregateByIndustry(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT education, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE jp.is_active = 1 " +
            "  AND education IS NOT NULL AND education != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY education " +
            "ORDER BY count DESC" +
            "</script>")
    List<Map<String, Object>> searchAggregateByEducation(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("<script>" +
            "SELECT experience, COUNT(*) AS count " +
            "FROM biz_job_posting jp " +
            "WHERE jp.is_active = 1 " +
            "  AND experience IS NOT NULL AND experience != '' " +
            "  AND jp.title LIKE CONCAT('%', #{plainKeyword}, '%') " +
            "GROUP BY experience " +
            "ORDER BY count DESC" +
            "</script>")
    List<Map<String, Object>> searchAggregateByExperience(
            @Param("keyword") String keyword,
            @Param("plainKeyword") String plainKeyword
    );

    @Select("SELECT id, title, company_name AS companyName, city, industry_name AS industryName, " +
            "salary_min AS salaryMin, salary_max AS salaryMax, salary_text AS salaryText, " +
            "publish_date AS publishDate " +
            "FROM biz_job_posting " +
            "WHERE is_active = 1 " +
            "ORDER BY publish_date DESC, salary_max DESC, salary_min DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> hotJobs(@Param("limit") int limit);

    @Select("<script>" +
            "SELECT DATE_FORMAT(publish_date, '%Y-%m') AS period, " +
            "  ROUND(AVG(salary_min), 2) AS avgSalaryMin, " +
            "  ROUND(AVG(salary_max), 2) AS avgSalaryMax, " +
            "  COUNT(*) AS jobCount " +
            "FROM biz_job_posting " +
            "WHERE is_active = 1 " +
            "  AND publish_date IS NOT NULL " +
            "  AND salary_min IS NOT NULL " +
            "  <if test=\"city != null and city != ''\"> " +
            "    AND city LIKE CONCAT('%', #{city}, '%') " +
            "  </if> " +
            "  <if test=\"industry != null and industry != ''\"> " +
            "    AND industry_name LIKE CONCAT('%', #{industry}, '%') " +
            "  </if> " +
            "GROUP BY DATE_FORMAT(publish_date, '%Y-%m') " +
            "ORDER BY period" +
            "</script>")
    List<Map<String, Object>> salaryTrend(
            @Param("city") String city,
            @Param("industry") String industry
    );

    @Select("SELECT s.skill_name " +
            "FROM biz_job_skill js " +
            "JOIN biz_skill s ON js.skill_id = s.id " +
            "WHERE js.job_id = #{jobId} " +
            "ORDER BY s.skill_name")
    List<String> jobSkills(@Param("jobId") Long jobId);

    @Select("<script>" +
            "SELECT " +
            "  jp.id, " +
            "  jp.title, " +
            "  jp.company_name AS companyName, " +
            "  jp.city, " +
            "  jp.industry_name AS industryName, " +
            "  jp.salary_text AS salaryText, " +
            "  jp.publish_date AS publishDate, " +
            "  COUNT(DISTINCT js.skill_id) AS overlapSkills, " +
            "  GROUP_CONCAT(DISTINCT s.skill_name ORDER BY s.skill_name SEPARATOR ', ') AS matchedSkills " +
            "FROM biz_job_posting jp " +
            "JOIN biz_job_skill js ON jp.id = js.job_id " +
            "JOIN biz_skill s ON js.skill_id = s.id " +
            "WHERE jp.is_active = 1 " +
            "  AND jp.id != #{jobId} " +
            "  AND js.skill_id IN ( " +
            "    SELECT skill_id FROM biz_job_skill WHERE job_id = #{jobId} " +
            "  ) " +
            "  <if test=\"city != null and city != ''\"> " +
            "    AND jp.city LIKE CONCAT('%', #{city}, '%') " +
            "  </if> " +
            "GROUP BY jp.id, jp.title, jp.company_name, jp.city, jp.industry_name, jp.salary_text, jp.publish_date " +
            "ORDER BY overlapSkills DESC, jp.publish_date DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> similarJobsBySkills(
            @Param("jobId") Long jobId,
            @Param("city") String city,
            @Param("limit") int limit
    );
}
