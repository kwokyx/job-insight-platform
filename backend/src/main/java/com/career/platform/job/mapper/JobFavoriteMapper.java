package com.career.platform.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.career.platform.job.entity.JobFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface JobFavoriteMapper extends BaseMapper<JobFavorite> {

    @Select("SELECT jp.id, jp.title, jp.company_name AS companyName, " +
            "COALESCE(jp.city, jp.job_city) AS city, jp.salary_raw AS salaryText, " +
            "jp.publish_date AS publishDate, f.note, f.created_at AS favoritedAt " +
            "FROM biz_job_favorite f " +
            "JOIN biz_job_posting jp ON f.job_id = jp.id " +
            "WHERE f.user_id = #{userId} " +
            "ORDER BY f.created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> userFavorites(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("SELECT COUNT(*) FROM biz_job_favorite WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);
}
