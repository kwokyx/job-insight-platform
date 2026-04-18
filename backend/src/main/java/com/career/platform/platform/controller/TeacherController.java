package com.career.platform.platform.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.entity.TeacherCourse;
import com.career.platform.platform.mapper.TeacherCourseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 教师功能控制器
 * - 课程管理（上传/编辑/删除课程大纲）
 * - 课程技能 vs 市场需求匹配分析
 */
@Tag(name = "教师功能", description = "课程管理与市场匹配分析")
@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    private final TeacherCourseMapper courseMapper;
    private final JobPostingMapper jobMapper;

    public TeacherController(TeacherCourseMapper courseMapper, JobPostingMapper jobMapper) {
        this.courseMapper = courseMapper;
        this.jobMapper = jobMapper;
    }

    // ─── 内部DTO ─────────────────

    public static class CourseRequest {
        @NotBlank(message = "课程名称不能为空")
        private String courseName;
        @NotBlank(message = "核心技能不能为空")
        private String coreSkills;
        private Integer creditHours;
        private String semester;
        private String major;
        private String description;

        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public String getCoreSkills() { return coreSkills; }
        public void setCoreSkills(String coreSkills) { this.coreSkills = coreSkills; }
        public Integer getCreditHours() { return creditHours; }
        public void setCreditHours(Integer creditHours) { this.creditHours = creditHours; }
        public String getSemester() { return semester; }
        public void setSemester(String semester) { this.semester = semester; }
        public String getMajor() { return major; }
        public void setMajor(String major) { this.major = major; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // ─── 课程 CRUD ─────────────────

    @Operation(summary = "我的课程列表")
    @GetMapping("/courses")
    public R<?> listCourses() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(courseMapper.listByTeacher(userId));
    }

    @Log("新增课程")
    @Operation(summary = "新增课程")
    @PostMapping("/courses")
    public R<?> addCourse(@Valid @RequestBody CourseRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeacherCourse course = new TeacherCourse();
        course.setUserId(userId);
        course.setCourseName(req.getCourseName());
        course.setCoreSkills(req.getCoreSkills());
        course.setCreditHours(req.getCreditHours());
        course.setSemester(req.getSemester());
        course.setMajor(req.getMajor());
        course.setDescription(req.getDescription());
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.insert(course);
        return R.ok("课程添加成功", course.getId());
    }

    @Log("更新课程")
    @Operation(summary = "更新课程")
    @PutMapping("/courses/{id}")
    public R<?> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeacherCourse course = courseMapper.selectById(id);
        if (course == null || !course.getUserId().equals(userId)) {
            throw BusinessException.notFound("课程不存在");
        }
        course.setCourseName(req.getCourseName());
        course.setCoreSkills(req.getCoreSkills());
        course.setCreditHours(req.getCreditHours());
        course.setSemester(req.getSemester());
        course.setMajor(req.getMajor());
        course.setDescription(req.getDescription());
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
        return R.ok("课程更新成功");
    }

    @Log("删除课程")
    @Operation(summary = "删除课程")
    @DeleteMapping("/courses/{id}")
    public R<?> deleteCourse(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeacherCourse course = courseMapper.selectById(id);
        if (course == null || !course.getUserId().equals(userId)) {
            throw BusinessException.notFound("课程不存在");
        }
        courseMapper.deleteById(id);
        return R.ok("课程删除成功");
    }

    // ─── 课程技能 vs 市场需求匹配分析 ─────────────────

    @Operation(summary = "课程技能与市场需求匹配分析")
    @GetMapping("/market-match")
    public R<?> marketMatchAnalysis() {
        Long userId = SecurityUtils.getCurrentUserId();

        // 1. 提取教师所有课程中的技能
        List<String> teacherSkills = courseMapper.allTeacherSkills(userId);
        if (teacherSkills.isEmpty()) {
            return R.fail("暂无课程数据，请先添加课程");
        }

        // 2. 获取市场Top技能
        List<Map<String, Object>> marketTopSkills = jobMapper.topSkills(50);
        Set<String> marketSkillSet = new LinkedHashSet<>();
        for (Map<String, Object> s : marketTopSkills) {
            String skill = String.valueOf(s.get("skill"));
            marketSkillSet.add(skill.toLowerCase().trim());
        }

        // 3. 对比分析
        Set<String> teacherSkillLower = new LinkedHashSet<>();
        for (String s : teacherSkills) {
            teacherSkillLower.add(s.toLowerCase().trim());
        }

        // 已覆盖的市场技能
        List<String> covered = new ArrayList<>();
        // 未覆盖的市场热门技能（脱节项）
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (Map<String, Object> ms : marketTopSkills) {
            String skillName = String.valueOf(ms.get("skill"));
            if (teacherSkillLower.contains(skillName.toLowerCase().trim())) {
                covered.add(skillName);
            } else {
                Map<String, Object> gap = new HashMap<>();
                gap.put("skill", skillName);
                gap.put("marketDemand", ms.get("count"));
                gaps.add(gap);
            }
        }

        // 课程中有但市场不热门的技能（可能过时）
        List<String> possiblyOutdated = new ArrayList<>();
        for (String ts : teacherSkills) {
            if (!marketSkillSet.contains(ts.toLowerCase().trim())) {
                possiblyOutdated.add(ts);
            }
        }

        double coverageRate = marketTopSkills.isEmpty() ? 0 :
                Math.round((double) covered.size() / Math.min(marketTopSkills.size(), 30) * 10000) / 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTeacherSkills", teacherSkills.size());
        result.put("totalMarketTopSkills", Math.min(marketTopSkills.size(), 30));
        result.put("coverageRate", coverageRate + "%");
        result.put("coveredSkills", covered);
        result.put("marketGaps", gaps.size() > 15 ? gaps.subList(0, 15) : gaps);
        result.put("possiblyOutdated", possiblyOutdated);
        result.put("recommendations", buildRecommendations(covered, gaps, possiblyOutdated, coverageRate));

        return R.ok(result);
    }

    private List<String> buildRecommendations(List<String> covered, List<Map<String, Object>> gaps,
                                              List<String> outdated, double coverageRate) {
        List<String> recommendations = new ArrayList<>();
        if (coverageRate < 40) {
            recommendations.add("课程技能覆盖率偏低（" + coverageRate + "%），建议大幅调整课程内容以匹配市场需求");
        } else if (coverageRate < 70) {
            recommendations.add("课程技能覆盖率一般（" + coverageRate + "%），建议针对性补充热门技能课程");
        } else {
            recommendations.add("课程技能覆盖率良好（" + coverageRate + "%），课程与市场需求对接较好");
        }

        if (!gaps.isEmpty()) {
            List<String> topGaps = new ArrayList<>();
            for (int i = 0; i < Math.min(5, gaps.size()); i++) {
                topGaps.add(String.valueOf(gaps.get(i).get("skill")));
            }
            recommendations.add("建议优先新增课程覆盖以下热门技能：" + String.join("、", topGaps));
        }

        if (!outdated.isEmpty() && outdated.size() > 3) {
            recommendations.add("有 " + outdated.size() + " 项课程技能在当前市场Top50中未出现，建议评估是否需要调整");
        }

        return recommendations;
    }
}
