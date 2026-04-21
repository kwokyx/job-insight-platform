package com.career.platform.platform.service;

import com.career.platform.platform.mapper.TeacherCourseMapper;
import com.career.platform.platform.entity.TeacherMaterialAsset;
import com.career.platform.platform.mapper.TeacherMaterialAssetMapper;
import com.career.platform.warehouse.entity.Curriculum;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import com.career.platform.warehouse.service.SupplyDemandService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TeachingReformService {

    private final TeacherCourseMapper teacherCourseMapper;
    private final CurriculumMapper curriculumMapper;
    private final TeacherMaterialAssetMapper teacherMaterialAssetMapper;
    private final SupplyDemandService supplyDemandService;
    private final MarketSkillService marketSkillService;
    private final ObjectMapper objectMapper;

    public TeachingReformService(TeacherCourseMapper teacherCourseMapper,
                                 CurriculumMapper curriculumMapper,
                                 TeacherMaterialAssetMapper teacherMaterialAssetMapper,
                                 SupplyDemandService supplyDemandService,
                                 MarketSkillService marketSkillService,
                                 ObjectMapper objectMapper) {
        this.teacherCourseMapper = teacherCourseMapper;
        this.curriculumMapper = curriculumMapper;
        this.teacherMaterialAssetMapper = teacherMaterialAssetMapper;
        this.supplyDemandService = supplyDemandService;
        this.marketSkillService = marketSkillService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildTeachingReformAnalysis(Long userId, String major) {
        List<Map<String, Object>> teacherCourses = loadCourseAssets(userId, major);
        List<String> teacherSkills = loadCourseSkills(userId, major);

        Map<String, Object> supplyDemand = supplyDemandService.analyzeCurriculumGap(major);
        List<Map<String, Object>> missingSkills = asMapList(supplyDemand.get("missingInSchool"));
        List<String> coveredSkills = toStringList(supplyDemand.get("matchedSkills"));

        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("major", StringUtils.hasText(major) ? major : inferMajor(teacherCourses));
        blueprint.put("courseCount", teacherCourses.size());
        blueprint.put("teacherSkillCount", teacherSkills.size());
        blueprint.put("capabilityDimensions", supplyDemand.getOrDefault("capabilityDimensions", Collections.emptyList()));
        blueprint.put("jobFamilies", supplyDemand.getOrDefault("jobFamilies", Collections.emptyList()));
        blueprint.put("graduationRequirements", buildGraduationRequirements(supplyDemand));
        blueprint.put("curriculumModules", buildCurriculumModules(teacherCourses, teacherSkills));
        blueprint.put("matrixRows", buildMatrixRows(userId, blueprint, teacherCourses, major));
        blueprint.put("reformActions", buildReformActions(missingSkills, supplyDemand));
        blueprint.put("assessmentSuggestions", buildAssessmentSuggestions(coveredSkills, missingSkills));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("major", blueprint.get("major"));
        result.put("teacherCourses", teacherCourses);
        result.put("teacherSkills", teacherSkills);
        result.put("blueprint", blueprint);
        result.put("supplyDemand", supplyDemand);
        result.put("governanceScorecard", buildGovernanceScorecard(teacherCourses, teacherSkills, supplyDemand));
        return result;
    }

    private List<Map<String, Object>> buildGraduationRequirements(Map<String, Object> supplyDemand) {
        List<Map<String, Object>> dimensions = asMapList(supplyDemand.get("capabilityDimensions"));
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> item : dimensions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", "GR-" + index);
            row.put("name", item.getOrDefault("dimension", "core-capability"));
            row.put("description", "能够围绕" + item.getOrDefault("dimension", "核心能力") + "完成岗位导向任务、输出项目成果并进行过程复盘。");
            row.put("priority", item.getOrDefault("priority", "P2"));
            result.add(row);
            index++;
            if (result.size() >= 6) {
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(requirement("GR-1", "岗位基础能力", "能够完成岗位基础任务并正确使用常见工具。", "P1"));
            result.add(requirement("GR-2", "项目交付能力", "能够围绕真实场景输出可展示的课程或项目成果。", "P1"));
            result.add(requirement("GR-3", "表达与协作能力", "能够将成果转换为简历、答辩或求职沟通材料。", "P2"));
        }
        return result;
    }

    private Map<String, Object> requirement(String code, String name, String description, String priority) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("description", description);
        row.put("priority", priority);
        return row;
    }

    private List<Map<String, Object>> buildCurriculumModules(List<Map<String, Object>> teacherCourses, List<String> teacherSkills) {
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Map<String, Object> course : teacherCourses) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseName", course.get("courseName"));
            row.put("major", course.get("major"));
            row.put("semester", course.get("semester"));
            row.put("creditHours", course.get("creditHours"));
            row.put("capabilityPoints", marketSkillService.cleanSkillNames(splitSkills(String.valueOf(course.get("coreSkills"))), 8));
            row.put("evidenceType", "project-demo");
            modules.add(row);
            if (modules.size() >= 8) {
                break;
            }
        }
        if (modules.isEmpty() && !teacherSkills.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("courseName", "核心能力训练模块");
            fallback.put("major", "");
            fallback.put("semester", "");
            fallback.put("creditHours", null);
            fallback.put("capabilityPoints", teacherSkills.subList(0, Math.min(6, teacherSkills.size())));
            fallback.put("evidenceType", "capstone-project");
            modules.add(fallback);
        }
        return modules;
    }

    private List<Map<String, Object>> buildMatrixRows(Long userId,
                                                      Map<String, Object> blueprint,
                                                      List<Map<String, Object>> teacherCourses,
                                                      String major) {
        List<Map<String, Object>> graduationRequirements = asMapList(blueprint.get("graduationRequirements"));
        List<Map<String, Object>> curriculumModules = asMapList(blueprint.get("curriculumModules"));
        Map<String, Object> syllabusSummary = loadLatestMaterialSummary(userId, "SYLLABUS", major);
        Map<String, Object> studentSummary = loadLatestMaterialSummary(userId, "STUDENT_STATUS", major);
        List<Map<String, Object>> syllabusSamples = toMapRows(syllabusSummary.get("samples"));
        List<Map<String, Object>> studentSamples = toMapRows(studentSummary.get("samples"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < graduationRequirements.size(); index++) {
            Map<String, Object> requirement = graduationRequirements.get(index);
            Map<String, Object> syllabusSample = pickSample(syllabusSamples, index);
            Map<String, Object> studentSample = pickSample(studentSamples, index);
            List<Map<String, Object>> matchedModules = resolveModulesForRequirement(requirement, curriculumModules, teacherCourses, index);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", requirement.get("code"));
            row.put("requirement", requirement.get("name"));
            row.put("priority", requirement.getOrDefault("priority", "P2"));
            row.put("modules", collectStrings(matchedModules, "courseName"));
            row.put("moduleCount", matchedModules.size());
            row.put("capabilityPoints", resolveCapabilityPoints(requirement, syllabusSample, matchedModules));
            row.put("evidence", resolveEvidenceList(syllabusSample, matchedModules));
            row.put("jobFamilies", resolveJobFamilies(studentSample, blueprint));
            row.put("studentFocus", resolveStudentFocus(studentSample));
            row.put("assessmentModes", splitSkills(stringValue(findValueLike(syllabusSample, "考核方式"))));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildReformActions(List<Map<String, Object>> missingSkills, Map<String, Object> supplyDemand) {
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> jobFamilies = asMapList(supplyDemand.get("jobFamilies"));
        String topTrack = jobFamilies.isEmpty() ? "重点岗位族" : String.valueOf(jobFamilies.get(0).get("jobFamily"));

        actions.add(action("P1", "围绕" + topTrack + "重排课程输出", "将课程作业、实训项目和毕业成果统一映射到岗位族能力要求。"));
        if (!missingSkills.isEmpty()) {
            actions.add(action("P1", "补齐高频缺口能力", "优先补充" + joinSkills(missingSkills, 4) + "相关训练单元，并建立分阶段考核。"));
        }
        actions.add(action("P2", "建立毕业要求与能力点矩阵", "把课程、能力点、岗位族、毕业要求放到同一张矩阵表中，便于教改立项和审核。"));
        actions.add(action("P2", "用作品证据替代纯知识考核", "课程结课应输出项目成果、技术说明、答辩材料和求职表达模板。"));
        actions.add(action("P3", "建立月度复盘机制", "每月复盘供需偏差、课程命中率和学生求职反馈，持续迭代课程体系。"));
        return actions;
    }

    private Map<String, Object> action(String priority, String title, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("priority", priority);
        row.put("title", title);
        row.put("detail", detail);
        return row;
    }

    private List<Map<String, Object>> buildAssessmentSuggestions(List<String> coveredSkills, List<Map<String, Object>> missingSkills) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(assessment("过程考核", "围绕真实岗位任务拆成若干里程碑，记录产出与复盘。"));
        if (!coveredSkills.isEmpty()) {
            items.add(assessment("课程成果", "将" + String.join("、", coveredSkills.subList(0, Math.min(3, coveredSkills.size()))) + "写入课程成果要求，必须形成可展示作品。"));
        }
        if (!missingSkills.isEmpty()) {
            items.add(assessment("专项补强", "针对" + joinSkills(missingSkills, 3) + "建立专项训练包和达标标准。"));
        }
        items.add(assessment("求职表达", "毕业前统一完成简历、项目介绍和岗位答辩演练。"));
        return items;
    }

    private Map<String, Object> assessment(String label, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("detail", detail);
        return row;
    }

    private Map<String, Object> buildGovernanceScorecard(List<Map<String, Object>> teacherCourses,
                                                         List<String> teacherSkills,
                                                         Map<String, Object> supplyDemand) {
        Map<String, Object> scorecard = new LinkedHashMap<>();
        int coverage = parsePercent(supplyDemand.get("matchRate"));
        int courses = teacherCourses.size();
        scorecard.put("courseAssetScore", Math.min(100, courses * 12));
        scorecard.put("capabilityCoverageScore", coverage);
        scorecard.put("marketAlignmentScore", Math.min(100, coverage + (courses >= 4 ? 10 : 0)));
        scorecard.put("evidenceReadinessScore", Math.min(100, teacherSkills.size() * 6));
        scorecard.put("overallScore", Math.min(100, (coverage + Math.min(100, courses * 12) + Math.min(100, teacherSkills.size() * 6)) / 3));
        return scorecard;
    }

    private String inferMajor(List<Map<String, Object>> teacherCourses) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> course : teacherCourses) {
            String major = String.valueOf(course.getOrDefault("major", "")).trim();
            if (StringUtils.hasText(major)) {
                counts.put(major, counts.getOrDefault(major, 0) + 1);
            }
        }
        String best = "";
        int max = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                best = entry.getKey();
                max = entry.getValue();
            }
        }
        return best;
    }

    private String joinSkills(List<Map<String, Object>> rows, int limit) {
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String skill = String.valueOf(row.getOrDefault("skill", "")).trim();
            if (StringUtils.hasText(skill)) {
                values.add(skill);
            }
            if (values.size() >= limit) {
                break;
            }
        }
        return values.isEmpty() ? "核心能力" : String.join("、", values);
    }

    private List<String> splitSkills(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        String[] parts = raw.split("[,，、/|；;]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private int parsePercent(Object value) {
        String raw = String.valueOf(value == null ? "" : value).replace("%", "").trim();
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                rows.add((Map<String, Object>) item);
            }
        }
        return rows;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            String text = String.valueOf(item == null ? "" : item).trim();
            if (StringUtils.hasText(text)) {
                result.add(text.toLowerCase(Locale.ROOT).equals(text) ? marketSkillService.normalizeSkillName(text) : text);
            }
        }
        return new ArrayList<>(result);
    }

    private List<Map<String, Object>> loadCourseAssets(Long userId, String major) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<Curriculum> curriculums = curriculumMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Curriculum>()
                        .eq(Curriculum::getUploadedBy, userId)
                        .eq(Curriculum::getIsActive, 1)
                        .like(StringUtils.hasText(major), Curriculum::getMajor, major)
                        .orderByDesc(Curriculum::getUpdatedAt)
        );
        if (curriculums.isEmpty()) {
            return teacherCourseMapper.listByTeacher(userId);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Curriculum item : curriculums) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("courseName", item.getCourseName());
            row.put("major", item.getMajor());
            row.put("semester", item.getSemester());
            row.put("creditHours", item.getCredit());
            row.put("description", item.getDescription());
            row.put("coreSkills", String.join(", ", extractCurriculumSkills(item)));
            rows.add(row);
        }
        return rows;
    }

    private List<String> loadCourseSkills(Long userId, String major) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<Curriculum> curriculums = curriculumMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Curriculum>()
                        .eq(Curriculum::getUploadedBy, userId)
                        .eq(Curriculum::getIsActive, 1)
                        .like(StringUtils.hasText(major), Curriculum::getMajor, major)
                        .orderByDesc(Curriculum::getUpdatedAt)
        );
        if (curriculums.isEmpty()) {
            return marketSkillService.cleanSkillNames(teacherCourseMapper.allTeacherSkills(userId), 80);
        }

        Set<String> skills = new LinkedHashSet<>();
        for (Curriculum item : curriculums) {
            skills.addAll(extractCurriculumSkills(item));
        }
        return marketSkillService.cleanSkillNames(new ArrayList<>(skills), 80);
    }

    private List<String> extractCurriculumSkills(Curriculum curriculum) {
        Set<String> skills = new LinkedHashSet<>();
        skills.addAll(splitSkills(curriculum.getKeywords()));
        skills.addAll(splitSkills(curriculum.getDescription()));
        return new ArrayList<>(skills);
    }

    private Map<String, Object> loadLatestMaterialSummary(Long userId, String materialType, String major) {
        List<TeacherMaterialAsset> assets = teacherMaterialAssetMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeacherMaterialAsset>()
                        .eq(TeacherMaterialAsset::getUserId, userId)
                        .eq(TeacherMaterialAsset::getMaterialType, materialType)
                        .and(StringUtils.hasText(major), w -> w.eq(TeacherMaterialAsset::getMajor, major)
                                .or().isNull(TeacherMaterialAsset::getMajor)
                                .or().eq(TeacherMaterialAsset::getMajor, ""))
                        .orderByDesc(TeacherMaterialAsset::getUpdatedAt)
                        .last("LIMIT 1")
        );
        TeacherMaterialAsset latest = assets.isEmpty() ? null : assets.get(0);
        if (latest == null && StringUtils.hasText(major)) {
            assets = teacherMaterialAssetMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeacherMaterialAsset>()
                            .eq(TeacherMaterialAsset::getUserId, userId)
                            .eq(TeacherMaterialAsset::getMaterialType, materialType)
                            .orderByDesc(TeacherMaterialAsset::getUpdatedAt)
                            .last("LIMIT 1")
            );
            latest = assets.isEmpty() ? null : assets.get(0);
        }
        if (latest == null || !StringUtils.hasText(latest.getSummaryJson())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(latest.getSummaryJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapRows(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                rows.add((Map<String, Object>) item);
            }
        }
        return rows;
    }

    private Map<String, Object> pickSample(List<Map<String, Object>> rows, int index) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.size() > index ? rows.get(index) : rows.get(0);
    }

    private List<Map<String, Object>> resolveModulesForRequirement(Map<String, Object> requirement,
                                                                   List<Map<String, Object>> curriculumModules,
                                                                   List<Map<String, Object>> teacherCourses,
                                                                   int index) {
        String requirementName = stringValue(requirement.get("name"));
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> module : curriculumModules) {
            List<String> points = toStringList(module.get("capabilityPoints"));
            boolean hit = points.stream().anyMatch(point ->
                    containsIgnoreCase(point, requirementName) || containsIgnoreCase(requirementName, point));
            if (hit) {
                matched.add(module);
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }
        if (curriculumModules.size() > index) {
            matched.add(curriculumModules.get(index));
            return matched;
        }
        if (teacherCourses.size() > index) {
            matched.add(teacherCourses.get(index));
        }
        return matched;
    }

    private List<String> resolveCapabilityPoints(Map<String, Object> requirement,
                                                 Map<String, Object> syllabusSample,
                                                 List<Map<String, Object>> matchedModules) {
        List<String> points = splitSkills(stringValue(findValueLike(syllabusSample, "能力点")));
        if (!points.isEmpty()) {
            return points;
        }
        for (Map<String, Object> module : matchedModules) {
            List<String> modulePoints = toStringList(module.get("capabilityPoints"));
            if (!modulePoints.isEmpty()) {
                return modulePoints;
            }
        }
        return splitSkills(stringValue(requirement.get("name")));
    }

    private List<String> resolveEvidenceList(Map<String, Object> syllabusSample,
                                             List<Map<String, Object>> matchedModules) {
        List<String> evidence = splitSkills(stringValue(findValueLike(syllabusSample, "实践环节")));
        if (evidence.isEmpty()) {
            evidence = splitSkills(stringValue(findValueLike(syllabusSample, "考核方式")));
        }
        if (!evidence.isEmpty()) {
            return evidence;
        }
        List<String> fallback = new ArrayList<>();
        for (Map<String, Object> module : matchedModules) {
            String evidenceType = stringValue(module.get("evidenceType"));
            if (StringUtils.hasText(evidenceType)) {
                fallback.add(evidenceType);
            }
        }
        return fallback;
    }

    private List<String> resolveJobFamilies(Map<String, Object> studentSample, Map<String, Object> blueprint) {
        List<String> families = splitSkills(stringValue(findValueLike(studentSample, "目标岗位族")));
        if (!families.isEmpty()) {
            return families;
        }
        return collectStrings(asMapList(blueprint.get("jobFamilies")), "jobFamily");
    }

    private String resolveStudentFocus(Map<String, Object> studentSample) {
        List<String> weakness = splitSkills(stringValue(findValueLike(studentSample, "能力短板")));
        if (!weakness.isEmpty()) {
            return String.join("、", weakness.subList(0, Math.min(3, weakness.size())));
        }
        return stringValue(findValueLike(studentSample, "重点帮扶"));
    }

    private List<String> collectStrings(List<Map<String, Object>> rows, String key) {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = stringValue(row.get(key)).trim();
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private Object findValueLike(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (containsIgnoreCase(entry.getKey(), key) || containsIgnoreCase(key, entry.getKey())) {
                return entry.getValue();
            }
        }
        return "";
    }

    private boolean containsIgnoreCase(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
