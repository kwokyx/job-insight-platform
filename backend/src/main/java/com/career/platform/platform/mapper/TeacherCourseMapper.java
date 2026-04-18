package com.career.platform.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.career.platform.platform.entity.TeacherCourse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TeacherCourseMapper extends BaseMapper<TeacherCourse> {

    /**
     * 查询教师的所有课程（合并同专业）
     */
    @Select("SELECT id, course_name AS courseName, core_skills AS coreSkills, " +
            "credit_hours AS creditHours, semester, major, description, created_at AS createdAt " +
            "FROM biz_teacher_course WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Map<String, Object>> listByTeacher(@Param("userId") Long userId);

    /**
     * 提取教师所有课程中的技能关键词（去重后的全集）
     */
    @Select("SELECT DISTINCT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(core_skills, ',', n.n), ',', -1)) AS skill " +
            "FROM biz_teacher_course " +
            "CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 " +
            "UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) n " +
            "WHERE user_id = #{userId} " +
            "AND n.n <= 1 + (LENGTH(core_skills) - LENGTH(REPLACE(core_skills, ',', ''))) " +
            "AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(core_skills, ',', n.n), ',', -1)) != ''")
    List<String> allTeacherSkills(@Param("userId") Long userId);
}
