package com.career.platform.platform.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.common.util.SecurityUtils;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.entity.TeacherCourse;
import com.career.platform.platform.mapper.TeacherCourseMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.TeachingReformService;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Tag(name = "教师功能", description = "课程管理与市场匹配分析")
@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    private final TeacherCourseMapper courseMapper;
    private final JobPostingMapper jobMapper;
    private final TeachingReformService teachingReformService;
    private final CurriculumMapper curriculumMapper;
    private final MarketSkillService marketSkillService;

    public TeacherController(TeacherCourseMapper courseMapper,
                             JobPostingMapper jobMapper,
                             TeachingReformService teachingReformService,
                             CurriculumMapper curriculumMapper,
                             MarketSkillService marketSkillService) {
        this.courseMapper = courseMapper;
        this.jobMapper = jobMapper;
        this.teachingReformService = teachingReformService;
        this.curriculumMapper = curriculumMapper;
        this.marketSkillService = marketSkillService;
    }

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

    @Operation(summary = "我的课程列表")
    @GetMapping("/courses")
    public R<?> listCourses(@RequestParam(required = false) Long ownerUserId) {
        Long userId = SecurityUtils.resolveOwnedUserId(ownerUserId);
        List<Map<String, Object>> curriculumRows = listOwnedCurriculums(userId);
        if (!curriculumRows.isEmpty()) {
            return R.ok(curriculumRows);
        }
        return R.ok(courseMapper.listByTeacher(userId));
    }

    @Log("新增课程")
    @Operation(summary = "新增课程")
    @PostMapping("/courses")
    public R<?> addCourse(@Valid @RequestBody CourseRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        Curriculum curriculum = new Curriculum();
        applyCourseRequestToCurriculum(curriculum, req, userId);
        curriculum.setCreatedAt(LocalDateTime.now());
        curriculum.setUpdatedAt(LocalDateTime.now());
        curriculumMapper.insert(curriculum);
        return R.ok("课程添加成功", curriculum.getId());
    }

    @Log("更新课程")
    @Operation(summary = "更新课程")
    @PutMapping("/courses/{id}")
    public R<?> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        Curriculum curriculum = loadOwnedCurriculum(id, userId);
        if (curriculum != null) {
            applyCourseRequestToCurriculum(curriculum, req, userId);
            curriculum.setUpdatedAt(LocalDateTime.now());
            curriculumMapper.updateById(curriculum);
            return R.ok("课程更新成功");
        }

        TeacherCourse course = courseMapper.selectById(id);
        if (course == null || !Objects.equals(course.getUserId(), userId)) {
            throw BusinessException.notFound("课程不存在");
        }
        course.setCourseName(req.getCourseName().trim());
        course.setCoreSkills(req.getCoreSkills().trim());
        course.setCreditHours(req.getCreditHours());
        course.setSemester(normalizeText(req.getSemester()));
        course.setMajor(normalizeText(req.getMajor()));
        course.setDescription(normalizeText(req.getDescription()));
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
        return R.ok("课程更新成功");
    }

    @Log("删除课程")
    @Operation(summary = "删除课程")
    @DeleteMapping("/courses/{id}")
    public R<?> deleteCourse(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Curriculum curriculum = loadOwnedCurriculum(id, userId);
        if (curriculum != null) {
            curriculum.setIsActive(0);
            curriculum.setUpdatedAt(LocalDateTime.now());
            curriculumMapper.updateById(curriculum);
            return R.ok("课程删除成功");
        }

        TeacherCourse course = courseMapper.selectById(id);
        if (course == null || !Objects.equals(course.getUserId(), userId)) {
            throw BusinessException.notFound("课程不存在");
        }
        courseMapper.deleteById(id);
        return R.ok("课程删除成功");
    }

    @Operation(summary = "课程技能与市场需求匹配分析")
    @GetMapping("/market-match")
    public R<?> marketMatchAnalysis(@RequestParam(required = false) String major) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> teacherSkills = loadTeacherSkills(userId);
        if (teacherSkills.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("totalTeacherSkills", 0);
            empty.put("totalMarketTopSkills", 0);
            empty.put("coverageRate", "0.0%");
            empty.put("coveredSkills", Collections.emptyList());
            empty.put("marketGaps", Collections.emptyList());
            empty.put("possiblyOutdated", Collections.emptyList());
            empty.put("recommendations", Collections.singletonList("当前还没有课程数据，请先新增课程或导入课程 Excel。"));
            return R.ok(empty);
        }

        String resolvedMajor = StringUtils.hasText(major) ? major.trim() : inferTeacherMajor(userId);
        List<Map<String, Object>> marketTopSkills = loadMarketTopSkillsForMajor(resolvedMajor, teacherSkills, 50);
        Set<String> marketSkillSet = new LinkedHashSet<>();
        for (Map<String, Object> row : marketTopSkills) {
            marketSkillSet.add(String.valueOf(row.get("skill")).toLowerCase(Locale.ROOT).trim());
        }

        Set<String> teacherSkillLower = new LinkedHashSet<>();
        for (String skill : teacherSkills) {
            teacherSkillLower.add(skill.toLowerCase(Locale.ROOT).trim());
        }

        List<String> covered = new ArrayList<>();
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (Map<String, Object> row : marketTopSkills) {
            String skillName = String.valueOf(row.get("skill"));
            if (teacherSkillLower.contains(skillName.toLowerCase(Locale.ROOT).trim())) {
                covered.add(skillName);
            } else {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("skill", skillName);
                gap.put("marketDemand", row.get("count"));
                gaps.add(gap);
            }
        }

        List<String> possiblyOutdated = new ArrayList<>();
        for (String skill : teacherSkills) {
            if (!marketSkillSet.contains(skill.toLowerCase(Locale.ROOT).trim())) {
                possiblyOutdated.add(skill);
            }
        }

        double coverageRate = marketTopSkills.isEmpty() ? 0D
                : Math.round((double) covered.size() / Math.min(marketTopSkills.size(), 30) * 10000D) / 100D;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTeacherSkills", teacherSkills.size());
        result.put("totalMarketTopSkills", Math.min(marketTopSkills.size(), 30));
        result.put("coverageRate", coverageRate + "%");
        result.put("coveredSkills", covered);
        result.put("marketGaps", gaps.size() > 15 ? gaps.subList(0, 15) : gaps);
        result.put("possiblyOutdated", possiblyOutdated);
        result.put("recommendations", buildRecommendations(covered, gaps, possiblyOutdated, coverageRate));
        result.put("major", resolvedMajor);
        result.put("scope", StringUtils.hasText(resolvedMajor) ? "major-related-jobs" : "platform-top-jobs");
        result.put("explicitMajor", StringUtils.hasText(major));
        return R.ok(result);
    }

    @Operation(summary = "教学改革分析")
    @GetMapping("/teaching-reform")
    public R<?> teachingReformAnalysis(@RequestParam(required = false) String major) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(teachingReformService.buildTeachingReformAnalysis(userId, major));
    }

    private List<Map<String, Object>> listOwnedCurriculums(Long userId) {
        List<Curriculum> curriculums = curriculumMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Curriculum>()
                        .eq(Curriculum::getUploadedBy, userId)
                        .eq(Curriculum::getIsActive, 1)
                        .orderByDesc(Curriculum::getUpdatedAt)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Curriculum curriculum : curriculums) {
            result.add(toCourseView(curriculum));
        }
        return result;
    }

    private Map<String, Object> toCourseView(Curriculum curriculum) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", curriculum.getId());
        row.put("courseName", curriculum.getCourseName());
        row.put("coreSkills", joinSkills(splitSkills(curriculum.getKeywords())));
        row.put("creditHours", curriculum.getCredit() == null ? null : curriculum.getCredit().intValue());
        row.put("semester", curriculum.getSemester());
        row.put("major", curriculum.getMajor());
        row.put("description", curriculum.getDescription());
        row.put("department", curriculum.getDepartment());
        row.put("createdAt", curriculum.getCreatedAt());
        row.put("updatedAt", curriculum.getUpdatedAt());
        row.put("source", "curriculum");
        return row;
    }

    private void applyCourseRequestToCurriculum(Curriculum curriculum, CourseRequest req, Long userId) {
        curriculum.setCourseName(req.getCourseName().trim());
        curriculum.setCourseCode(null);
        curriculum.setDepartment(null);
        curriculum.setMajor(normalizeText(req.getMajor()));
        curriculum.setCredit(req.getCreditHours() == null ? null : BigDecimal.valueOf(req.getCreditHours()));
        curriculum.setSemester(normalizeText(req.getSemester()));
        curriculum.setDescription(normalizeText(req.getDescription()));
        curriculum.setKeywords(writeSkills(req.getCoreSkills()));
        curriculum.setIsActive(1);
        curriculum.setUploadedBy(userId);
    }

    private Curriculum loadOwnedCurriculum(Long id, Long userId) {
        Curriculum curriculum = curriculumMapper.selectById(id);
        if (curriculum == null || curriculum.getIsActive() == null || curriculum.getIsActive() != 1) {
            return null;
        }
        if (!Objects.equals(curriculum.getUploadedBy(), userId)) {
            throw BusinessException.notFound("课程不存在");
        }
        return curriculum;
    }

    private String writeSkills(String rawSkills) {
        List<String> skills = splitSkills(rawSkills);
        if (skills.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(skills.get(i).replace("\"", "\\\"")).append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private String joinSkills(List<String> skills) {
        return skills == null || skills.isEmpty() ? "" : String.join(", ", skills);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> loadTeacherSkills(Long userId) {
        List<Curriculum> curriculums = curriculumMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Curriculum>()
                        .eq(Curriculum::getUploadedBy, userId)
                        .eq(Curriculum::getIsActive, 1)
                        .orderByDesc(Curriculum::getUpdatedAt)
        );
        if (curriculums.isEmpty()) {
            return courseMapper.allTeacherSkills(userId);
        }

        Set<String> skills = new LinkedHashSet<>();
        for (Curriculum item : curriculums) {
            skills.addAll(splitSkills(item.getKeywords()));
            skills.addAll(splitSkills(item.getDescription()));
        }
        return new ArrayList<>(skills);
    }

    private String inferTeacherMajor(Long userId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Curriculum> curriculums = curriculumMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Curriculum>()
                        .eq(Curriculum::getUploadedBy, userId)
                        .eq(Curriculum::getIsActive, 1)
                        .orderByDesc(Curriculum::getUpdatedAt)
        );
        for (Curriculum item : curriculums) {
            if (StringUtils.hasText(item.getMajor())) {
                String key = item.getMajor().trim();
                counts.put(key, counts.getOrDefault(key, 0) + 1);
            }
        }
        List<Map<String, Object>> teacherCourses = courseMapper.listByTeacher(userId);
        for (Map<String, Object> course : teacherCourses) {
            String major = String.valueOf(course.getOrDefault("major", "")).trim();
            if (StringUtils.hasText(major)) {
                counts.put(major, counts.getOrDefault(major, 0) + 1);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private List<Map<String, Object>> loadMarketTopSkillsForMajor(String major, List<String> teacherSkills, int limit) {
        List<String> keywords = buildMajorJobKeywords(major, teacherSkills);
        List<Map<String, Object>> rawRows = Collections.emptyList();
        if (!keywords.isEmpty()) {
            rawRows = jobMapper.topSkillsByJobKeywords(keywords, Math.max(limit * 4, 80));
        }
        List<Map<String, Object>> cleaned = marketSkillService.cleanSkillRows(rawRows, limit);
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        if (isTechMajor(major)) {
            return marketSkillService.topTechnicalSkills(limit);
        }
        return marketSkillService.topSkills(limit);
    }

    private boolean isTechMajor(String major) {
        String text = major == null ? "" : major.toLowerCase(Locale.ROOT);
        return text.contains("计算机")
                || text.contains("软件")
                || text.contains("网络")
                || text.contains("大数据")
                || text.contains("人工智能")
                || text.contains("信息")
                || text.contains("前端")
                || text.contains("后端")
                || text.contains("开发")
                || text.contains("数据");
    }

    private List<String> buildMajorJobKeywords(String major, List<String> teacherSkills) {
        if (!StringUtils.hasText(major)) {
            return Collections.emptyList();
        }
        String text = major.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (text.contains("计算机") || text.contains("软件") || text.contains("信息")) {
            Collections.addAll(keywords, "Java", "后端", "前端", "运维", "测试", "开发", "软件", "程序员", "全栈", "实施");
        }
        if (text.contains("网络")) {
            Collections.addAll(keywords, "网络", "运维", "安全", "云计算", "Linux", "系统");
        }
        if (text.contains("大数据") || text.contains("数据")) {
            Collections.addAll(keywords, "数据", "大数据", "数据分析", "数据开发", "数据工程师", "BI", "数仓");
        }
        if (text.contains("人工智能") || text.contains("智能") || text.contains("算法")) {
            Collections.addAll(keywords, "算法", "人工智能", "机器学习", "深度学习", "NLP", "推荐");
        }
        if (text.contains("电子商务") || text.contains("电商")) {
            Collections.addAll(keywords, "电商", "运营", "新媒体", "内容运营", "平台运营");
        }
        if (text.contains("财务") || text.contains("会计")) {
            Collections.addAll(keywords, "会计", "财务", "审计", "税务", "出纳");
        }
        if (text.contains("市场") || text.contains("营销")) {
            Collections.addAll(keywords, "营销", "市场", "品牌", "投放", "新媒体");
        }
        if (text.contains("机械")) {
            Collections.addAll(keywords, "机械", "制造", "工艺", "设备", "自动化");
        }
        if (keywords.isEmpty()) {
            keywords.add(major.trim());
        }
        keywords.addAll(inferTrackKeywords(teacherSkills));
        return new ArrayList<>(keywords);
    }

    private List<String> inferTrackKeywords(List<String> teacherSkills) {
        if (teacherSkills == null || teacherSkills.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("frontend", 0);
        scores.put("backend", 0);
        scores.put("data", 0);
        scores.put("ops", 0);
        scores.put("ai", 0);
        scores.put("testing", 0);

        for (String raw : teacherSkills) {
            String skill = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
            if (skill.contains("vue") || skill.contains("react") || skill.contains("javascript")
                    || skill.contains("typescript") || skill.contains("html") || skill.contains("css")
                    || skill.contains("前端")) {
                scores.put("frontend", scores.get("frontend") + 2);
            }
            if (skill.contains("java") || skill.contains("spring") || skill.contains("mysql")
                    || skill.contains("redis") || skill.contains("mybatis") || skill.contains("后端")
                    || skill.contains("接口")) {
                scores.put("backend", scores.get("backend") + 2);
            }
            if (skill.contains("python") || skill.contains("pandas") || skill.contains("sql")
                    || skill.contains("hive") || skill.contains("spark") || skill.contains("flink")
                    || skill.contains("bi") || skill.contains("数据")) {
                scores.put("data", scores.get("data") + 2);
            }
            if (skill.contains("linux") || skill.contains("docker") || skill.contains("k8s")
                    || skill.contains("kubernetes") || skill.contains("nginx") || skill.contains("运维")
                    || skill.contains("云")) {
                scores.put("ops", scores.get("ops") + 2);
            }
            if (skill.contains("ai") || skill.contains("算法") || skill.contains("机器学习")
                    || skill.contains("深度学习") || skill.contains("nlp") || skill.contains("推荐")) {
                scores.put("ai", scores.get("ai") + 2);
            }
            if (skill.contains("测试") || skill.contains("selenium") || skill.contains("jmeter")
                    || skill.contains("pytest")) {
                scores.put("testing", scores.get("testing") + 2);
            }
        }

        List<String> result = new ArrayList<>();
        scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .filter(entry -> entry.getValue() > 0)
                .limit(2)
                .forEach(entry -> result.addAll(trackKeywords(entry.getKey())));
        return result;
    }

    private List<String> trackKeywords(String track) {
        switch (track) {
            case "frontend":
                return Arrays.asList("前端", "Vue", "React", "小程序", "Web前端");
            case "backend":
                return Arrays.asList("后端", "Java", "Spring", "服务端", "开发工程师");
            case "data":
                return Arrays.asList("数据分析", "数据开发", "数据工程师", "BI", "数仓");
            case "ops":
                return Arrays.asList("运维", "Linux", "云计算", "DevOps", "网络");
            case "ai":
                return Arrays.asList("算法", "人工智能", "机器学习", "深度学习", "推荐");
            case "testing":
                return Arrays.asList("测试", "自动化测试", "性能测试", "测试开发");
            default:
                return Collections.emptyList();
        }
    }

    private List<String> splitSkills(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        String cleaned = raw.replace("[", ",")
                .replace("]", ",")
                .replace("\"", ",")
                .replace("'", ",");
        String[] parts = cleaned.split("[,，、\\s]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private List<String> buildRecommendations(List<String> covered,
                                              List<Map<String, Object>> gaps,
                                              List<String> outdated,
                                              double coverageRate) {
        List<String> recommendations = new ArrayList<>();
        if (coverageRate < 40) {
            recommendations.add("课程技能覆盖率偏低（" + coverageRate + "%），建议优先调整课程内容以匹配市场需求。");
        } else if (coverageRate < 70) {
            recommendations.add("课程技能覆盖率一般（" + coverageRate + "%），建议针对性补齐热门技能。");
        } else {
            recommendations.add("课程技能覆盖率较好（" + coverageRate + "%），课程与市场需求对齐程度较高。");
        }

        if (!gaps.isEmpty()) {
            List<String> topGaps = new ArrayList<>();
            for (int i = 0; i < Math.min(5, gaps.size()); i++) {
                topGaps.add(String.valueOf(gaps.get(i).get("skill")));
            }
            recommendations.add("建议优先补充以下热门技能： " + String.join("、", topGaps));
        }

        if (!outdated.isEmpty() && outdated.size() > 3) {
            recommendations.add("有 " + outdated.size() + " 项课程技能未出现在当前热门技能池中，建议评估是否需要调整。");
        }
        return recommendations;
    }
}
