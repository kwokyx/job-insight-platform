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
        Map<String, Object> syllabusSummary = loadLatestMaterialSummary(userId, "SYLLABUS", major);
        Map<String, Object> studentSummary = loadLatestMaterialSummary(userId, "STUDENT_STATUS", major);

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
        blueprint.put("matrixRows", buildMatrixRows(blueprint, teacherCourses, syllabusSummary, studentSummary));
        blueprint.put("materialReadiness", buildMaterialReadiness(teacherCourses, syllabusSummary, studentSummary));
        blueprint.put("courseActionPlans", buildPrioritizedCourseActionPlans(teacherCourses, missingSkills, blueprint, syllabusSummary, studentSummary));
        blueprint.put("reformActions", buildReformActions(
                missingSkills,
                supplyDemand,
                asMapList(blueprint.get("courseActionPlans")),
                asMapList(blueprint.get("materialReadiness"))
        ));
        blueprint.put("assessmentSuggestions", buildAssessmentSuggestions(coveredSkills, missingSkills));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("major", blueprint.get("major"));
        result.put("teacherCourses", teacherCourses);
        result.put("teacherSkills", teacherSkills);
        result.put("blueprint", blueprint);
        result.put("supplyDemand", supplyDemand);
        result.put("governanceScorecard", buildGovernanceScorecard(
                teacherCourses,
                teacherSkills,
                supplyDemand,
                asMapList(blueprint.get("materialReadiness")),
                asMapList(blueprint.get("courseActionPlans"))
        ));
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

    private List<Map<String, Object>> buildMatrixRows(Map<String, Object> blueprint,
                                                      List<Map<String, Object>> teacherCourses,
                                                      Map<String, Object> syllabusSummary,
                                                      Map<String, Object> studentSummary) {
        List<Map<String, Object>> graduationRequirements = asMapList(blueprint.get("graduationRequirements"));
        List<Map<String, Object>> curriculumModules = asMapList(blueprint.get("curriculumModules"));
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

    private List<Map<String, Object>> buildReformActions(List<Map<String, Object>> missingSkills,
                                                         Map<String, Object> supplyDemand,
                                                         List<Map<String, Object>> courseActionPlans,
                                                         List<Map<String, Object>> materialReadiness) {
        List<Map<String, Object>> actions = buildReformActions(missingSkills, supplyDemand);
        if (!courseActionPlans.isEmpty()) {
            Map<String, Object> firstPlan = courseActionPlans.get(0);
            actions.add(0, action(
                    stringValue(firstPlan.getOrDefault("priority", "P1")),
                    "优先整改 " + stringValue(firstPlan.getOrDefault("courseName", "核心课程")),
                    stringValue(firstPlan.getOrDefault("reason", "围绕课程级证据和岗位缺口优先落地整改动作。"))
            ));
        }
        long missingMaterials = materialReadiness.stream().filter(item -> !Boolean.TRUE.equals(item.get("ready"))).count();
        if (missingMaterials > 0) {
            actions.add(Math.min(actions.size(), 1), action("P1", "先补齐教改证据链", "当前仍有 " + missingMaterials + " 类资料未形成稳定证据链，建议先补全模板字段映射，再启动正式教改立项。"));
        }
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

    private Map<String, Object> buildGovernanceScorecard(List<Map<String, Object>> teacherCourses,
                                                         List<String> teacherSkills,
                                                         Map<String, Object> supplyDemand,
                                                         List<Map<String, Object>> materialReadiness,
                                                         List<Map<String, Object>> courseActionPlans) {
        Map<String, Object> scorecard = buildGovernanceScorecard(teacherCourses, teacherSkills, supplyDemand);
        int coverage = parsePercent(supplyDemand.get("matchRate"));
        int courses = teacherCourses.size();
        int readyMaterials = 0;
        int totalEvidenceRows = 0;
        for (Map<String, Object> item : materialReadiness) {
            if (Boolean.TRUE.equals(item.get("ready"))) {
                readyMaterials++;
            }
            totalEvidenceRows += parseInt(item.get("rowCount"));
        }
        int highPriorityPlans = 0;
        for (Map<String, Object> item : courseActionPlans) {
            if ("P1".equalsIgnoreCase(stringValue(item.get("priority")))) {
                highPriorityPlans++;
            }
        }

        int courseAssetScore = Math.min(100, courses * 12);
        int capabilityCoverageScore = Math.min(100, coverage + Math.min(12, teacherSkills.size() / 4));
        int marketAlignmentScore = Math.max(0, Math.min(100, coverage + (courses >= 4 ? 10 : 0) - Math.min(15, highPriorityPlans * 3)));
        int evidenceReadinessScore = Math.min(100, readyMaterials * 24 + Math.min(28, totalEvidenceRows));
        int governanceExecutionScore = Math.max(30, 100 - Math.min(45, highPriorityPlans * 8));
        int overallScore = Math.min(100, (courseAssetScore + capabilityCoverageScore + marketAlignmentScore + evidenceReadinessScore + governanceExecutionScore) / 5);

        scorecard.put("courseAssetScore", courseAssetScore);
        scorecard.put("capabilityCoverageScore", capabilityCoverageScore);
        scorecard.put("marketAlignmentScore", marketAlignmentScore);
        scorecard.put("evidenceReadinessScore", evidenceReadinessScore);
        scorecard.put("governanceExecutionScore", governanceExecutionScore);
        scorecard.put("overallScore", overallScore);
        scorecard.put("summary", "治理得分已经改成可解释指标，不只看课程数量，也看资料证据、岗位对齐和课程级整改准备。");

        List<Map<String, Object>> dimensions = new ArrayList<>();
        dimensions.add(scoreDimension("课程资产", courseAssetScore, "课程数 " + courses + " / 技能条目 " + teacherSkills.size(), "课程库越完整，教改治理基础越稳。"));
        dimensions.add(scoreDimension("能力覆盖", capabilityCoverageScore, "供需覆盖率 " + coverage + "%", "覆盖率决定课程是否真正承接岗位能力。"));
        dimensions.add(scoreDimension("市场对齐", marketAlignmentScore, "P1 整改 " + highPriorityPlans + " 项", "高优先级整改越多，说明当前对齐压力越大。"));
        dimensions.add(scoreDimension("证据化准备", evidenceReadinessScore, "资料完备 " + readyMaterials + "/" + materialReadiness.size(), "教学大纲和学生情况真正入链后，建议才更可信。"));
        dimensions.add(scoreDimension("执行准备", governanceExecutionScore, "课程级整改 " + courseActionPlans.size() + " 项", "整改任务越具体，越容易进入学院治理闭环。"));
        scorecard.put("dimensions", dimensions);

        List<String> risks = new ArrayList<>();
        if (readyMaterials < 2) {
            risks.add("教学大纲和学生情况资料仍偏弱，建议先补证据链。");
        }
        if (coverage < 35) {
            risks.add("课程技能覆盖率偏低，短期内应优先调整核心课程供给。");
        }
        if (highPriorityPlans >= 3) {
            risks.add("高优先级课程整改较多，说明专业课程体系与岗位需求仍有明显偏差。");
        }
        scorecard.put("risks", risks);
        return scorecard;
    }

    private Map<String, Object> scoreDimension(String label, int score, String evidence, String interpretation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("score", score);
        row.put("evidence", evidence);
        row.put("interpretation", interpretation);
        return row;
    }

    private List<Map<String, Object>> buildMaterialReadiness(List<Map<String, Object>> teacherCourses,
                                                             Map<String, Object> syllabusSummary,
                                                             Map<String, Object> studentSummary) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(materialReadinessItem(
                "课程清单",
                !teacherCourses.isEmpty(),
                teacherCourses.size(),
                teacherCourses.isEmpty() ? "尚未形成教师课程资产" : "课程库已可用于供需分析与课程蓝图"
        ));
        rows.add(materialReadinessItem(
                "教学大纲",
                !syllabusSummary.isEmpty(),
                parseInt(syllabusSummary.get("rowCount")),
                !syllabusSummary.isEmpty() ? firstHeaderSummary(syllabusSummary, "课程目标、能力点、毕业要求会进入矩阵与考核建议") : "未上传或未解析到大纲摘要"
        ));
        rows.add(materialReadinessItem(
                "学生情况",
                !studentSummary.isEmpty(),
                parseInt(studentSummary.get("rowCount")),
                !studentSummary.isEmpty() ? firstHeaderSummary(studentSummary, "能力短板、目标岗位族和帮扶对象会进入整改排序") : "未上传或未解析到学生画像摘要"
        ));
        return rows;
    }

    private Map<String, Object> materialReadinessItem(String name, boolean ready, int rowCount, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("ready", ready);
        row.put("rowCount", rowCount);
        row.put("detail", detail);
        return row;
    }

    private String firstHeaderSummary(Map<String, Object> summary, String fallback) {
        List<String> headers = toStringList(summary.get("headers"));
        if (headers.isEmpty()) {
            return fallback;
        }
        return "已识别字段：" + String.join("、", headers.subList(0, Math.min(4, headers.size())));
    }

    private List<Map<String, Object>> buildCourseActionPlans(List<Map<String, Object>> teacherCourses,
                                                             List<Map<String, Object>> missingSkills,
                                                             Map<String, Object> blueprint,
                                                             Map<String, Object> syllabusSummary,
                                                             Map<String, Object> studentSummary) {
        List<Map<String, Object>> syllabusSamples = toMapRows(syllabusSummary.get("samples"));
        List<Map<String, Object>> studentSamples = toMapRows(studentSummary.get("samples"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < teacherCourses.size() && index < 8; index++) {
            Map<String, Object> course = teacherCourses.get(index);
            Map<String, Object> syllabusSample = matchCourseSample(syllabusSamples, course, index);
            Map<String, Object> studentSample = pickSample(studentSamples, index);
            List<String> courseSkills = marketSkillService.cleanSkillNames(splitSkills(stringValue(course.get("coreSkills"))), 8);
            List<String> capabilityPoints = resolveCapabilityPoints(course, syllabusSample, Collections.singletonList(course));
            List<String> assessmentModes = extractByAliases(syllabusSample, "考核方式", "assessment", "assessmentMode");
            List<String> evidenceList = resolveEvidenceList(syllabusSample, Collections.singletonList(course));
            List<String> studentWeakness = extractByAliases(studentSample, "能力短板", "weakness", "skillGap");
            List<String> targetFamilies = resolveJobFamilies(studentSample, blueprint);
            List<Map<String, Object>> courseMissingRows = selectMissingSkillsForCourse(courseSkills, missingSkills, capabilityPoints);
            List<String> courseMissingSkills = collectStrings(courseMissingRows, "skill");
            List<String> actions = new ArrayList<>();
            if (!courseMissingSkills.isEmpty()) {
                actions.add("补齐 " + String.join("、", courseMissingSkills) + " 相关训练任务。");
                Map<String, Object> strongestGap = courseMissingRows.get(0);
                String adjust = stringValue(strongestGap.get("moduleAdjustment"));
                if (StringUtils.hasText(adjust)) {
                    actions.add(adjust);
                }
            }
            if (assessmentModes.isEmpty()) {
                actions.add("补过程考核与项目答辩，避免课程只有知识点输入没有成果证据。");
            }
            if (capabilityPoints.isEmpty()) {
                actions.add("补能力点与毕业要求映射，明确这门课到底承接什么培养目标。");
            }
            if (!studentWeakness.isEmpty()) {
                actions.add("针对学生短板 " + String.join("、", studentWeakness.subList(0, Math.min(2, studentWeakness.size()))) + " 设计专题补强。");
            }
            if (actions.isEmpty()) {
                actions.add("保持课程结构，重点把现有成果进一步固化成可展示作品与答辩材料。");
            }

            int riskScore = 0;
            int marketGapRisk = calculateCourseMarketGapRisk(courseMissingRows);
            riskScore += marketGapRisk;
            if (assessmentModes.isEmpty()) riskScore += 25;
            if (capabilityPoints.isEmpty()) riskScore += 20;
            if (!studentWeakness.isEmpty()) riskScore += 15;
            riskScore = Math.min(100, riskScore);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseName", course.get("courseName"));
            row.put("major", course.get("major"));
            row.put("semester", course.get("semester"));
            row.put("priority", riskScore >= 60 ? "P1" : riskScore >= 30 ? "P2" : "P3");
            row.put("riskScore", riskScore);
            row.put("matchedSkills", courseSkills);
            row.put("missingSkills", courseMissingSkills);
            row.put("missingSkillRows", courseMissingRows);
            row.put("capabilityPoints", capabilityPoints);
            row.put("assessmentModes", assessmentModes);
            row.put("evidenceList", evidenceList);
            row.put("jobFamilies", targetFamilies);
            row.put("studentWeakness", studentWeakness);
            row.put("actions", actions);
            row.put("reason", buildCourseReason(courseMissingSkills, assessmentModes, capabilityPoints, studentWeakness, targetFamilies));
            row.put("evidenceSummary", buildEvidenceSummary(syllabusSample, studentSample));
            rows.add(row);
        }
        return rows;
    }

    private int calculateCourseMarketGapRisk(List<Map<String, Object>> courseMissingRows) {
        if (courseMissingRows == null || courseMissingRows.isEmpty()) {
            return 0;
        }
        double demand = 0D;
        for (Map<String, Object> row : courseMissingRows) {
            demand += parseDouble(row.get("marketDemand"));
        }
        if (demand >= 180D) return 55;
        if (demand >= 120D) return 48;
        if (demand >= 80D) return 40;
        if (demand >= 40D) return 30;
        return 20;
    }

    private String buildCourseReason(List<String> courseMissingSkills,
                                     List<String> assessmentModes,
                                     List<String> capabilityPoints,
                                     List<String> studentWeakness,
                                     List<String> targetFamilies) {
        List<String> pieces = new ArrayList<>();
        if (!courseMissingSkills.isEmpty()) {
            pieces.add("课程尚未覆盖 " + String.join("、", courseMissingSkills));
        }
        if (assessmentModes.isEmpty()) {
            pieces.add("缺少明确考核方式");
        }
        if (capabilityPoints.isEmpty()) {
            pieces.add("能力点映射不足");
        }
        if (!studentWeakness.isEmpty()) {
            pieces.add("学生短板集中在 " + String.join("、", studentWeakness.subList(0, Math.min(2, studentWeakness.size()))));
        }
        if (!targetFamilies.isEmpty()) {
            pieces.add("目标岗位族主要是 " + String.join("、", targetFamilies.subList(0, Math.min(2, targetFamilies.size()))));
        }
        return pieces.isEmpty() ? "当前课程结构相对稳定，建议继续强化成果表达与就业转化。" : String.join("；", pieces) + "。";
    }

    private String buildEvidenceSummary(Map<String, Object> syllabusSample, Map<String, Object> studentSample) {
        List<String> evidence = new ArrayList<>();
        String objective = stringValue(findValueByAliases(syllabusSample, "课程目标", "objective", "goal"));
        String support = stringValue(findValueByAliases(studentSample, "重点帮扶", "support", "focus"));
        if (StringUtils.hasText(objective)) {
            evidence.add("课程目标：" + objective);
        }
        if (StringUtils.hasText(support)) {
            evidence.add("学生支持：" + support);
        }
        return evidence.isEmpty() ? "当前主要依据课程技能与市场缺口生成建议。" : String.join(" | ", evidence);
    }

    private Map<String, Object> matchCourseSample(List<Map<String, Object>> rows, Map<String, Object> course, int index) {
        String courseName = stringValue(course.get("courseName"));
        for (Map<String, Object> row : rows) {
            Object value = findValueByAliases(row, "课程名称", "courseName", "课程");
            if (containsIgnoreCase(stringValue(value), courseName) || containsIgnoreCase(courseName, stringValue(value))) {
                return row;
            }
        }
        return pickSample(rows, index);
    }

    private List<Map<String, Object>> selectMissingSkillsForCourse(List<String> courseSkills,
                                                                   List<Map<String, Object>> missingSkills,
                                                                   List<String> capabilityPoints) {
        List<String> baseSkillPool = new ArrayList<>(courseSkills);
        baseSkillPool.addAll(capabilityPoints);
        String courseDimensionHint = inferCourseDimension(baseSkillPool);

        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Map<String, Object> item : missingSkills) {
            String skill = stringValue(item.get("skill"));
            if (!StringUtils.hasText(skill)) {
                continue;
            }
            double coverageSimilarity = 0D;
            for (String base : baseSkillPool) {
                coverageSimilarity = Math.max(coverageSimilarity, marketSkillService.skillSimilarity(skill, base));
            }
            if (coverageSimilarity >= 0.72D) {
                continue;
            }

            String dimension = stringValue(item.get("dimension"));
            if (!StringUtils.hasText(dimension)) {
                dimension = marketSkillService.inferCapabilityDimension(skill);
            }
            double demand = parseDouble(item.get("marketDemand"));
            double dimensionBoost = containsIgnoreCase(dimension, courseDimensionHint) ? 1.12D : 1D;
            double gapScore = demand * (1D - coverageSimilarity) * dimensionBoost;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skill", skill);
            row.put("marketDemand", demand);
            row.put("dimension", dimension);
            row.put("coverageSimilarity", round2(coverageSimilarity));
            row.put("gapScore", round2(gapScore));
            row.put("moduleAdjustment", item.get("moduleAdjustment"));
            ranked.add(row);
        }
        ranked.sort((left, right) -> Double.compare(parseDouble(right.get("gapScore")), parseDouble(left.get("gapScore"))));
        if (ranked.size() > 3) {
            return new ArrayList<>(ranked.subList(0, 3));
        }
        return ranked;
    }

    private String inferCourseDimension(List<String> courseSkills) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String skill : courseSkills) {
            String dim = marketSkillService.inferCapabilityDimension(skill);
            counts.put(dim, counts.getOrDefault(dim, 0) + 1);
        }
        String best = "岗位综合能力";
        int max = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private List<String> extractByAliases(Map<String, Object> row, String... aliases) {
        return splitSkills(stringValue(findValueByAliases(row, aliases)));
    }

    private Object findValueByAliases(Map<String, Object> row, String... aliases) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        for (String alias : aliases) {
            Object value = findValueLike(row, alias);
            if (StringUtils.hasText(stringValue(value))) {
                return value;
            }
        }
        return "";
    }

    private List<Map<String, Object>> buildPrioritizedCourseActionPlans(List<Map<String, Object>> teacherCourses,
                                                                        List<Map<String, Object>> missingSkills,
                                                                        Map<String, Object> blueprint,
                                                                        Map<String, Object> syllabusSummary,
                                                                        Map<String, Object> studentSummary) {
        List<Map<String, Object>> rows = buildCourseActionPlans(teacherCourses, missingSkills, blueprint, syllabusSummary, studentSummary);
        for (Map<String, Object> row : rows) {
            List<String> missing = toStringList(row.get("missingSkills"));
            List<String> evidence = toStringList(row.get("evidenceList"));
            List<String> capability = toStringList(row.get("capabilityPoints"));
            List<String> weaknesses = toStringList(row.get("studentWeakness"));
            List<String> families = toStringList(row.get("jobFamilies"));

            int marketGapScore = missing.isEmpty() ? 0 : Math.min(45, missing.size() * 15);
            int evidenceGapScore = (toStringList(row.get("assessmentModes")).isEmpty() ? 18 : 0) + (evidence.isEmpty() ? 12 : 0);
            int capabilityGapScore = capability.isEmpty() ? 20 : Math.max(0, 16 - capability.size() * 4);
            int studentRiskScore = Math.min(16, weaknesses.size() * 8);
            int jobFamilyPressureScore = Math.min(12, Math.max(0, families.size() - 1) * 4);
            int riskScore = Math.min(100, marketGapScore + evidenceGapScore + capabilityGapScore + studentRiskScore + jobFamilyPressureScore);
            int confidenceScore = buildCoursePlanConfidenceScore(stringValue(row.get("evidenceSummary")), evidence, capability, families);

            row.put("riskScore", riskScore);
            row.put("confidenceScore", confidenceScore);
            row.put("priority", riskScore >= 60 ? "P1" : riskScore >= 30 ? "P2" : "P3");
            row.put("riskBreakdown", buildRiskBreakdown(marketGapScore, evidenceGapScore, capabilityGapScore, studentRiskScore, jobFamilyPressureScore));
        }
        rows.sort((left, right) -> {
            int riskCompare = Integer.compare(parseInt(right.get("riskScore")), parseInt(left.get("riskScore")));
            if (riskCompare != 0) {
                return riskCompare;
            }
            return Integer.compare(parseInt(right.get("confidenceScore")), parseInt(left.get("confidenceScore")));
        });
        return rows;
    }

    private int buildCoursePlanConfidenceScore(String evidenceSummary,
                                               List<String> evidenceList,
                                               List<String> capabilityPoints,
                                               List<String> targetFamilies) {
        int score = 42;
        if (StringUtils.hasText(evidenceSummary) && !evidenceSummary.contains("甯傚満缂哄彛")) {
            score += 24;
        }
        if (!evidenceList.isEmpty()) {
            score += 12;
        }
        if (!capabilityPoints.isEmpty()) {
            score += 12;
        }
        if (!targetFamilies.isEmpty()) {
            score += 10;
        }
        return Math.min(100, score);
    }

    private List<Map<String, Object>> buildRiskBreakdown(int marketGapScore,
                                                         int evidenceGapScore,
                                                         int capabilityGapScore,
                                                         int studentRiskScore,
                                                         int jobFamilyPressureScore) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(riskPart("市场缺口", marketGapScore));
        rows.add(riskPart("证据缺口", evidenceGapScore));
        rows.add(riskPart("能力点缺口", capabilityGapScore));
        rows.add(riskPart("学生短板压力", studentRiskScore));
        rows.add(riskPart("岗位族压力", jobFamilyPressureScore));
        return rows;
    }

    private Map<String, Object> riskPart(String label, int score) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("score", score);
        return row;
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

    private int parseInt(Object value) {
        String raw = stringValue(value).trim();
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double parseDouble(Object value) {
        String raw = stringValue(value).trim();
        try {
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
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
