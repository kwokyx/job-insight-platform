package com.career.platform.warehouse.service;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SupplyDemandService {

    private static final Logger log = LoggerFactory.getLogger(SupplyDemandService.class);
    private static final double COVERED_THRESHOLD = 0.78D;
    private static final double PARTIAL_THRESHOLD = 0.55D;
    private static final String ALGORITHM_VERSION = "supply-demand-v2.1";
    private static final String SKILL_DICTIONARY_VERSION = "market-skill-2026.04";

    private final JdbcTemplate jdbc;
    private final JobPostingMapper jobPostingMapper;
    private final MarketSkillService marketSkillService;

    public SupplyDemandService(JdbcTemplate jdbc,
                               JobPostingMapper jobPostingMapper,
                               MarketSkillService marketSkillService) {
        this.jdbc = jdbc;
        this.jobPostingMapper = jobPostingMapper;
        this.marketSkillService = marketSkillService;
    }

    public Map<String, Object> analyzeSkyDemandGap(String major) {
        String majorText = StringUtils.hasText(major) ? major.trim() : "";
        List<String> courseSkills = loadCourseSkills(majorText);
        List<Map<String, Object>> marketSkills = loadMarketSkills(majorText, courseSkills);

        Set<String> courseSet = new LinkedHashSet<>();
        for (String skill : courseSkills) {
            courseSet.add(skill.toLowerCase(Locale.ROOT));
        }

        List<Map<String, Object>> missingInSchool = new ArrayList<>();
        List<Map<String, Object>> partialInSchool = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        double demandTotal = 0D;
        double demandCovered = 0D;
        double demandPartial = 0D;

        for (Map<String, Object> market : marketSkills) {
            String marketSkill = stringValue(market.get("skill"));
            double demand = toDouble(market.get("count"));
            demandTotal += demand;

            MatchHit hit = findBestCourseHit(marketSkill, courseSkills);
            Map<String, Object> scored = scoredSkillItem(marketSkill, demand, hit);

            if (hit.similarity >= COVERED_THRESHOLD) {
                matched.add(marketSkill);
                demandCovered += demand;
                continue;
            }
            if (hit.similarity >= PARTIAL_THRESHOLD) {
                partialInSchool.add(scored);
                demandPartial += demand;
                continue;
            }
            scored.put("diagnosis", "岗位需求高，但当前课程/大纲缺少稳定覆盖");
            String dimension = marketSkillService.inferCapabilityDimension(marketSkill);
            scored.put("dimension", dimension);
            scored.put("urgency", demand >= 80 ? "P1" : demand >= 35 ? "P2" : "P3");
            scored.put("moduleAdjustment", buildModuleAdjustment(dimension, marketSkill));
            scored.put("assessmentEvidence", buildAssessmentEvidence(dimension, marketSkill));
            missingInSchool.add(scored);
        }

        List<Map<String, Object>> redundantInSchool = buildPotentiallyRedundantSkills(courseSkills, marketSkills);
        List<Map<String, Object>> capabilityDimensions = buildCapabilityDimensions(missingInSchool, matched);
        List<Map<String, Object>> jobFamilies = buildJobFamilies(majorText, courseSkills);
        List<Map<String, Object>> syllabusAdjustments = buildSyllabusAdjustments(missingInSchool, jobFamilies);
        List<Map<String, Object>> curriculumActions = buildCurriculumActions(missingInSchool, redundantInSchool, syllabusAdjustments);

        double weightedCoverage = demandTotal <= 0D ? 0D : (demandCovered * 100D / demandTotal);
        double weightedPartial = demandTotal <= 0D ? 0D : (demandPartial * 100D / demandTotal);
        double strictCoverage = marketSkills.isEmpty() ? 0D : (matched.size() * 100D / marketSkills.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("major", majorText);
        result.put("courseKeywordsCount", courseSkills.size());
        result.put("courseKeywordsSample", courseSkills.subList(0, Math.min(20, courseSkills.size())));
        result.put("marketSkillsCount", marketSkills.size());
        result.put("missingInSchool", missingInSchool.subList(0, Math.min(35, missingInSchool.size())));
        result.put("partialInSchool", partialInSchool.subList(0, Math.min(25, partialInSchool.size())));
        result.put("redundantInSchool", redundantInSchool.subList(0, Math.min(25, redundantInSchool.size())));
        result.put("matchedSkills", matched);
        result.put("matchRate", String.format(Locale.ROOT, "%.1f%%", strictCoverage));
        result.put("weightedCoverageRate", String.format(Locale.ROOT, "%.1f%%", weightedCoverage));
        result.put("weightedPartialRate", String.format(Locale.ROOT, "%.1f%%", weightedPartial));
        result.put("schoolMissingSkills", result.get("missingInSchool"));
        result.put("schoolRedundantSkills", result.get("redundantInSchool"));
        result.put("matchedSkillCount", matched.size());
        result.put("capabilityDimensions", capabilityDimensions);
        result.put("jobFamilies", jobFamilies);
        result.put("graduationRequirements", buildGraduationRequirements(capabilityDimensions));
        result.put("curriculumActions", curriculumActions);
        result.put("syllabusAdjustments", syllabusAdjustments);
        result.put("sampleMeta", buildSampleMeta(courseSkills.size(), marketSkills.size(), missingInSchool.size()));
        result.put("confidenceScore", confidenceScore(courseSkills.size(), marketSkills.size(), weightedCoverage, partialInSchool.size()));
        result.put("algorithmMeta", buildAlgorithmMeta());
        result.put("dataQuality", buildDataQuality(courseSkills.size(), marketSkills.size(), demandTotal, weightedCoverage));
        result.put("healthFlags", buildHealthFlags(missingInSchool, partialInSchool, weightedCoverage, weightedPartial, marketSkills.size()));
        result.put("summary", String.format(
                Locale.ROOT,
                "课程技能 %d 项，目标岗位技能样本 %d 项。严格匹配率 %.1f%%，需求加权覆盖率 %.1f%%，部分覆盖 %.1f%%，高价值缺口 %d 项。",
                courseSkills.size(), marketSkills.size(), strictCoverage, weightedCoverage, weightedPartial, missingInSchool.size()
        ));
        return result;
    }

    public Map<String, Object> analyzeCurriculumGap(String major) {
        return analyzeSkyDemandGap(major);
    }

    private List<String> loadCourseSkills(String major) {
        String baseQuery = "SELECT DISTINCT JSON_UNQUOTE(jt.keyword) AS keyword " +
                "FROM biz_curriculum c, JSON_TABLE(c.keywords, '$[*]' COLUMNS (keyword JSON PATH '$')) AS jt " +
                "WHERE c.is_active = 1";
        List<String> courseKeywords = new ArrayList<>();
        try {
            List<Map<String, Object>> rows;
            if (StringUtils.hasText(major)) {
                rows = jdbc.queryForList(baseQuery + " AND c.major LIKE ?", "%" + major + "%");
            } else {
                rows = jdbc.queryForList(baseQuery);
            }
            for (Map<String, Object> row : rows) {
                String kw = stringValue(row.get("keyword"));
                if (StringUtils.hasText(kw)) {
                    courseKeywords.add(kw);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load curriculum skills, fallback to empty set: {}", e.getMessage());
        }
        return marketSkillService.cleanSkillNames(courseKeywords, 300);
    }

    private List<Map<String, Object>> loadMarketSkills(String major, List<String> courseSkills) {
        List<String> keywords = buildMajorJobKeywords(major, courseSkills);
        List<Map<String, Object>> raw;
        if (!keywords.isEmpty()) {
            raw = jobPostingMapper.topSkillsByJobKeywords(keywords, 300);
        } else {
            raw = jobPostingMapper.topSkills(240);
        }
        List<Map<String, Object>> cleaned = marketSkillService.cleanSkillRows(raw, 120);

        if (isTechMajor(major)) {
            List<Map<String, Object>> technical = new ArrayList<>();
            for (Map<String, Object> row : cleaned) {
                String skill = stringValue(row.get("skill"));
                if (marketSkillService.isTechnicalSkill(skill)) {
                    technical.add(row);
                }
            }
            if (!technical.isEmpty()) {
                return technical.size() > 100 ? new ArrayList<>(technical.subList(0, 100)) : technical;
            }
        }
        return cleaned.size() > 100 ? new ArrayList<>(cleaned.subList(0, 100)) : cleaned;
    }

    private MatchHit findBestCourseHit(String marketSkill, List<String> courseSkills) {
        MatchHit best = new MatchHit("", 0D);
        for (String courseSkill : courseSkills) {
            double similarity = marketSkillService.skillSimilarity(marketSkill, courseSkill);
            if (similarity > best.similarity) {
                best = new MatchHit(courseSkill, similarity);
            }
        }
        return best;
    }

    private Map<String, Object> scoredSkillItem(String skill, double demand, MatchHit hit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("skill", skill);
        item.put("marketDemand", demand);
        item.put("closestCourseSkill", hit.skill);
        item.put("similarity", round2(hit.similarity));
        item.put("relevance", hit.similarity >= COVERED_THRESHOLD ? "high" : hit.similarity >= PARTIAL_THRESHOLD ? "medium" : "low");
        return item;
    }

    private List<Map<String, Object>> buildPotentiallyRedundantSkills(List<String> courseSkills,
                                                                      List<Map<String, Object>> marketSkills) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String skill : courseSkills) {
            MatchHit hit = findBestMarketHit(skill, marketSkills);
            if (hit.similarity >= 0.45D) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skill", skill);
            row.put("closestMarketSkill", hit.skill);
            row.put("similarity", round2(hit.similarity));
            row.put("diagnosis", "课程中出现，但与当前目标岗位高频技能关联较弱");
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(o -> toDouble(o.get("similarity"))));
        return rows;
    }

    private MatchHit findBestMarketHit(String courseSkill, List<Map<String, Object>> marketSkills) {
        MatchHit best = new MatchHit("", 0D);
        for (Map<String, Object> row : marketSkills) {
            String marketSkill = stringValue(row.get("skill"));
            double similarity = marketSkillService.skillSimilarity(courseSkill, marketSkill);
            if (similarity > best.similarity) {
                best = new MatchHit(marketSkill, similarity);
            }
        }
        return best;
    }

    private List<Map<String, Object>> buildCapabilityDimensions(List<Map<String, Object>> missingInSchool, List<String> matched) {
        Map<String, Double> demandByDimension = new LinkedHashMap<>();
        Map<String, Integer> countByDimension = new LinkedHashMap<>();
        for (Map<String, Object> row : missingInSchool) {
            String dimension = stringValue(row.get("dimension"));
            if (!StringUtils.hasText(dimension)) {
                dimension = marketSkillService.inferCapabilityDimension(stringValue(row.get("skill")));
            }
            double demand = toDouble(row.get("marketDemand"));
            demandByDimension.put(dimension, demandByDimension.getOrDefault(dimension, 0D) + demand);
            countByDimension.put(dimension, countByDimension.getOrDefault(dimension, 0) + 1);
        }
        if (!matched.isEmpty()) {
            countByDimension.put("已覆盖基础能力", matched.size());
            demandByDimension.put("已覆盖基础能力", matched.size() * 1.0D);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countByDimension.entrySet()) {
            String dimension = entry.getKey();
            int count = entry.getValue();
            double demand = demandByDimension.getOrDefault(dimension, 0D);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dimension", dimension);
            row.put("count", count);
            row.put("weightedDemand", round2(demand));
            row.put("priority", demand >= 120D || count >= 5 ? "P1" : demand >= 50D || count >= 2 ? "P2" : "P3");
            rows.add(row);
        }
        rows.sort((a, b) -> Double.compare(toDouble(b.get("weightedDemand")), toDouble(a.get("weightedDemand"))));
        return rows;
    }

    private List<Map<String, Object>> buildJobFamilies(String major, List<String> courseSkills) {
        List<String> keywords = buildMajorJobKeywords(major, courseSkills);
        List<Map<String, Object>> rows;
        if (keywords.isEmpty()) {
            rows = jdbc.queryForList(
                    "SELECT grouped.job_family AS jobFamily, COUNT(*) AS demand, " +
                            "ROUND(AVG(COALESCE(grouped.salary_min, 0)), 2) AS avgSalary " +
                            "FROM (" +
                            "  SELECT COALESCE(job_classification, industry_name, '未分类') AS job_family, salary_min " +
                            "  FROM biz_job_posting " +
                            "  WHERE COALESCE(job_classification, industry_name) IS NOT NULL" +
                            ") grouped " +
                            "GROUP BY grouped.job_family " +
                            "ORDER BY demand DESC LIMIT 8"
            );
        } else {
            StringBuilder sql = new StringBuilder(
                    "SELECT grouped.job_family AS jobFamily, COUNT(*) AS demand, " +
                            "ROUND(AVG(COALESCE(grouped.salary_min, 0)), 2) AS avgSalary " +
                            "FROM (" +
                            "  SELECT COALESCE(job_classification, industry_name, '未分类') AS job_family, salary_min, title, job_classification, industry_name " +
                            "  FROM biz_job_posting " +
                            "  WHERE COALESCE(job_classification, industry_name) IS NOT NULL" +
                            ") grouped WHERE ("
            );
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append(" grouped.title LIKE ? OR grouped.job_classification LIKE ? OR grouped.industry_name LIKE ? ");
                String like = "%" + keywords.get(i) + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }
            sql.append(") GROUP BY grouped.job_family ORDER BY demand DESC LIMIT 8");
            rows = jdbc.queryForList(sql.toString(), params.toArray());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobFamily", row.get("jobFamily"));
            item.put("demand", row.get("demand"));
            item.put("avgSalary", row.get("avgSalary"));
            item.put("majorHint", major);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildGraduationRequirements(List<Map<String, Object>> dimensions) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> row : dimensions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", "GR-" + index);
            item.put("name", row.get("dimension"));
            item.put("requirement", "毕业时应能够在" + row.get("dimension") + "方向完成岗位导向任务并提供成果证据。");
            item.put("priority", row.get("priority"));
            item.put("weightedDemand", row.get("weightedDemand"));
            result.add(item);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> buildCurriculumActions(List<Map<String, Object>> missingInSchool,
                                                             List<Map<String, Object>> redundantInSchool,
                                                             List<Map<String, Object>> syllabusAdjustments) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (!missingInSchool.isEmpty()) {
            actions.add(action("P1", "补齐高价值能力缺口", "优先补齐 " + firstSkills(missingInSchool, 4) + "，并进入必修模块或毕业设计任务。"));
        }
        if (!syllabusAdjustments.isEmpty()) {
            actions.add(action("P1", "按能力缺口重构教学大纲", "把大纲按“能力点-训练任务-考核证据”重排，先执行前 " +
                    Math.min(6, syllabusAdjustments.size()) + " 条高优先级整改项。"));
        }
        if (!redundantInSchool.isEmpty()) {
            actions.add(action("P2", "缩减低相关教学内容", "对 " + firstSkillsByKey(redundantInSchool, "skill", 3) + " 等低相关内容降权，释放学时给高需求能力点。"));
        }
        actions.add(action("P2", "建立月度岗位样本复盘", "按月更新岗位技能分布、课程覆盖率和教学调整闭环，避免课程与市场脱节。"));
        return actions;
    }

    private List<Map<String, Object>> buildSyllabusAdjustments(List<Map<String, Object>> missingInSchool,
                                                               List<Map<String, Object>> jobFamilies) {
        List<Map<String, Object>> adjustments = new ArrayList<>();
        String jobHint = firstSkillsByKey(jobFamilies, "jobFamily", 2);
        int rank = 1;
        for (Map<String, Object> missing : missingInSchool) {
            String skill = stringValue(missing.get("skill"));
            String dimension = stringValue(missing.get("dimension"));
            double demand = toDouble(missing.get("marketDemand"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put("skill", skill);
            row.put("dimension", dimension);
            row.put("urgency", demand >= 80 ? "P1" : demand >= 35 ? "P2" : "P3");
            row.put("marketDemand", demand);
            row.put("recommendedHours", demand >= 80 ? 10 : demand >= 35 ? 6 : 4);
            row.put("moduleAdjustment", buildModuleAdjustment(dimension, skill));
            row.put("teachingActivities", buildTeachingActivity(dimension, skill));
            row.put("assessmentEvidence", buildAssessmentEvidence(dimension, skill));
            row.put("alignmentReason", "该能力在目标岗位（" + (StringUtils.hasText(jobHint) ? jobHint : "核心岗位族") + "）中需求高，当前课程覆盖不足。");
            adjustments.add(row);
            rank++;
            if (adjustments.size() >= 15) {
                break;
            }
        }
        return adjustments;
    }

    private String buildModuleAdjustment(String dimension, String skill) {
        if ("工程开发能力".equals(dimension)) {
            return "新增“" + skill + "工程实战”专题，并要求完成可部署项目。";
        }
        if ("数据分析与建模能力".equals(dimension)) {
            return "将“" + skill + "”并入数据分析主线模块，增加业务数据集训练。";
        }
        if ("系统与云平台能力".equals(dimension)) {
            return "将“" + skill + "”嵌入运维与云平台实训，覆盖部署、监控与故障恢复。";
        }
        if ("算法与智能应用能力".equals(dimension)) {
            return "补充“" + skill + "”实验单元，要求模型实现与效果复盘。";
        }
        return "围绕“" + skill + "”补齐岗位任务型训练。";
    }

    private String buildTeachingActivity(String dimension, String skill) {
        if ("工程开发能力".equals(dimension)) {
            return "采用 Sprint 制项目实训，要求代码评审、接口联调和性能压测。";
        }
        if ("数据分析与建模能力".equals(dimension)) {
            return "采用案例驱动分析，要求完成数据清洗、建模、可视化与业务结论输出。";
        }
        if ("系统与云平台能力".equals(dimension)) {
            return "采用演练式教学，要求完成环境部署、日志诊断和容灾演练。";
        }
        if ("算法与智能应用能力".equals(dimension)) {
            return "采用问题驱动实验，要求完成特征工程、模型调参和误差分析。";
        }
        return "采用任务驱动教学，要求完成真实岗位场景模拟。";
    }

    private String buildAssessmentEvidence(String dimension, String skill) {
        if ("工程开发能力".equals(dimension)) {
            return "提交代码仓库、接口文档、测试报告和线上演示视频。";
        }
        if ("数据分析与建模能力".equals(dimension)) {
            return "提交分析报告、可视化看板、建模 notebook 与复盘文档。";
        }
        if ("系统与云平台能力".equals(dimension)) {
            return "提交部署脚本、监控面板截图、故障处理记录与复盘报告。";
        }
        if ("算法与智能应用能力".equals(dimension)) {
            return "提交模型实验记录、指标对比表、误差分析与改进方案。";
        }
        return "提交阶段成果物与岗位任务答辩材料。";
    }

    private Map<String, Object> action(String priority, String title, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("priority", priority);
        row.put("title", title);
        row.put("detail", detail);
        return row;
    }

    private Map<String, Object> buildSampleMeta(int courseSkillCount, int marketSkillCount, int missingCount) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("courseKeywordCount", courseSkillCount);
        meta.put("marketSkillCount", marketSkillCount);
        meta.put("missingSkillCount", missingCount);
        meta.put("method", "major-job-filter + skill-normalization + weighted-similarity-gap");
        meta.put("coveredThreshold", COVERED_THRESHOLD);
        meta.put("partialThreshold", PARTIAL_THRESHOLD);
        return meta;
    }

    private Map<String, Object> buildAlgorithmMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("algorithmVersion", ALGORITHM_VERSION);
        meta.put("skillDictionaryVersion", SKILL_DICTIONARY_VERSION);
        meta.put("matchingStrategy", "normalized-skill-similarity");
        meta.put("rankingStrategy", "marketDemand * (1 - similarity)");
        meta.put("generatedAt", System.currentTimeMillis());
        return meta;
    }

    private Map<String, Object> buildDataQuality(int courseSkillCount,
                                                 int marketSkillCount,
                                                 double demandTotal,
                                                 double weightedCoverage) {
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("courseSkillCount", courseSkillCount);
        quality.put("marketSkillCount", marketSkillCount);
        quality.put("marketDemandTotal", round2(demandTotal));
        quality.put("weightedCoverageRate", round2(weightedCoverage));
        quality.put("sampleAdequacy", courseSkillCount >= 20 && marketSkillCount >= 60 ? "HIGH"
                : courseSkillCount >= 10 && marketSkillCount >= 30 ? "MEDIUM" : "LOW");
        quality.put("qualityScore", qualityScore(courseSkillCount, marketSkillCount, weightedCoverage));
        return quality;
    }

    private int qualityScore(int courseSkillCount, int marketSkillCount, double weightedCoverage) {
        int score = 40;
        score += Math.min(25, courseSkillCount);
        score += Math.min(20, marketSkillCount / 3);
        score += Math.min(15, (int) Math.round(weightedCoverage / 8D));
        return Math.max(20, Math.min(98, score));
    }

    private List<Map<String, Object>> buildHealthFlags(List<Map<String, Object>> missingInSchool,
                                                       List<Map<String, Object>> partialInSchool,
                                                       double weightedCoverage,
                                                       double weightedPartial,
                                                       int marketSkillCount) {
        List<Map<String, Object>> flags = new ArrayList<>();
        if (marketSkillCount < 25) {
            flags.add(flag("LOW_SAMPLE", "WARN", "岗位技能样本偏少，建议扩大时间窗或放宽岗位过滤条件。"));
        }
        if (weightedCoverage < 25D) {
            flags.add(flag("LOW_COVERAGE", "CRITICAL", "需求加权覆盖率低于 25%，建议优先重排核心课程。"));
        } else if (weightedCoverage < 40D) {
            flags.add(flag("LOW_COVERAGE", "WARN", "需求加权覆盖率偏低，建议按缺口优先级逐步整改。"));
        }
        if (weightedPartial > 35D) {
            flags.add(flag("PARTIAL_ONLY", "WARN", "存在较多“部分覆盖”能力，建议把弱覆盖升级为可考核成果。"));
        }
        if (missingInSchool.size() >= 20) {
            flags.add(flag("GAP_OVERLOAD", "CRITICAL", "高价值缺口过多，建议按岗位族拆分分阶段整改。"));
        }
        if (missingInSchool.isEmpty() && partialInSchool.isEmpty()) {
            flags.add(flag("HEALTHY", "INFO", "供需匹配状态良好，建议保持月度复盘。"));
        }
        return flags;
    }

    private Map<String, Object> flag(String code, String level, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("level", level);
        row.put("detail", detail);
        return row;
    }

    private int confidenceScore(int courseSkillCount, int marketSkillCount, double weightedCoverage, int partialCount) {
        int score = 45;
        score += Math.min(22, courseSkillCount / 2);
        score += Math.min(18, marketSkillCount / 5);
        score += Math.min(10, (int) Math.round(weightedCoverage / 10D));
        score += partialCount > 0 ? 5 : 0;
        return Math.max(35, Math.min(96, score));
    }

    private String firstSkills(List<Map<String, Object>> rows, int limit) {
        return firstSkillsByKey(rows, "skill", limit);
    }

    private String firstSkillsByKey(List<Map<String, Object>> rows, String key, int limit) {
        List<String> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String val = stringValue(row.get(key));
            if (StringUtils.hasText(val)) {
                items.add(val);
            }
            if (items.size() >= limit) {
                break;
            }
        }
        return items.isEmpty() ? "核心能力" : String.join("、", items);
    }

    private List<String> buildMajorJobKeywords(String major, List<String> courseSkills) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        String text = stringValue(major).toLowerCase(Locale.ROOT);
        if (text.contains("计算机") || text.contains("软件") || text.contains("信息")) {
            Collections.addAll(keywords, "Java", "后端", "前端", "运维", "测试", "全栈", "开发", "软件");
        }
        if (text.contains("大数据") || text.contains("数据")) {
            Collections.addAll(keywords, "大数据", "数据分析", "数据开发", "数据工程师", "数仓", "BI");
        }
        if (text.contains("人工智能") || text.contains("智能") || text.contains("算法")) {
            Collections.addAll(keywords, "算法", "机器学习", "深度学习", "AI");
        }
        if (text.contains("网络")) {
            Collections.addAll(keywords, "网络", "运维", "安全", "云计算", "Linux");
        }
        for (String skill : courseSkills) {
            if (marketSkillService.isTechnicalSkill(skill)) {
                keywords.add(skill);
            }
            if (keywords.size() >= 20) {
                break;
            }
        }
        return new ArrayList<>(keywords);
    }

    private boolean isTechMajor(String major) {
        String text = stringValue(major).toLowerCase(Locale.ROOT);
        return text.contains("计算机")
                || text.contains("软件")
                || text.contains("大数据")
                || text.contains("人工智能")
                || text.contains("网络")
                || text.contains("信息")
                || text.contains("算法");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? 0D : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private static class MatchHit {
        private final String skill;
        private final double similarity;

        private MatchHit(String skill, double similarity) {
            this.skill = skill;
            this.similarity = similarity;
        }
    }
}
