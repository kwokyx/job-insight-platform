package com.career.platform.warehouse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SupplyDemandService {

    private static final Logger log = LoggerFactory.getLogger(SupplyDemandService.class);

    private final JdbcTemplate jdbc;

    public SupplyDemandService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> analyzeSkyDemandGap(String major) {
        Map<String, Object> result = new LinkedHashMap<>();

        String baseQuery = "SELECT DISTINCT JSON_UNQUOTE(jt.keyword) AS keyword " +
                "FROM biz_curriculum c, JSON_TABLE(c.keywords, '$[*]' COLUMNS (keyword JSON PATH '$')) AS jt " +
                "WHERE c.is_active = 1";

        List<String> courseKeywords = new ArrayList<>();
        try {
            List<Map<String, Object>> rows;
            if (major != null && !major.isEmpty()) {
                rows = jdbc.queryForList(baseQuery + " AND c.major LIKE ?", "%" + major + "%");
            } else {
                rows = jdbc.queryForList(baseQuery);
            }
            for (Map<String, Object> row : rows) {
                Object kw = row.get("keyword");
                if (kw != null) {
                    courseKeywords.add(kw.toString().trim().toLowerCase());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse curriculum keywords: {}", e.getMessage());
        }

        result.put("courseKeywordsCount", courseKeywords.size());
        result.put("courseKeywordsSample", courseKeywords.subList(0, Math.min(20, courseKeywords.size())));

        List<Map<String, Object>> marketSkills = jdbc.queryForList(
                "SELECT d.label_name AS skill, COUNT(*) AS demand " +
                        "FROM job_label_rel r " +
                        "JOIN job_label_dict d ON r.label_id = d.id " +
                        "GROUP BY d.id, d.label_name ORDER BY demand DESC LIMIT 100"
        );
        Set<String> marketSkillNames = new LinkedHashSet<>();
        for (Map<String, Object> row : marketSkills) {
            marketSkillNames.add(String.valueOf(row.get("skill")).trim().toLowerCase());
        }
        result.put("marketSkillsCount", marketSkillNames.size());

        Set<String> courseSet = new HashSet<>(courseKeywords);

        List<Map<String, Object>> missingInSchool = new ArrayList<>();
        for (Map<String, Object> ms : marketSkills) {
            String skill = String.valueOf(ms.get("skill")).trim().toLowerCase();
            if (!courseSet.contains(skill)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skill", ms.get("skill"));
                item.put("marketDemand", ms.get("demand"));
                item.put("diagnosis", "企业高需求但课程未覆盖");
                missingInSchool.add(item);
            }
        }
        result.put("missingInSchool", missingInSchool.subList(0, Math.min(30, missingInSchool.size())));

        List<String> redundantInSchool = new ArrayList<>();
        for (String keyword : courseKeywords) {
            if (!marketSkillNames.contains(keyword)) {
                redundantInSchool.add(keyword);
            }
        }
        result.put("redundantInSchool", redundantInSchool);

        List<String> matched = new ArrayList<>();
        for (String keyword : courseKeywords) {
            if (marketSkillNames.contains(keyword)) {
                matched.add(keyword);
            }
        }
        result.put("matchedSkills", matched);

        double matchRate = courseKeywords.isEmpty() ? 0 : (matched.size() * 100.0 / courseKeywords.size());
        result.put("matchRate", String.format("%.1f%%", matchRate));
        result.put("schoolMissingSkills", result.get("missingInSchool"));
        result.put("schoolRedundantSkills", redundantInSchool);
        result.put("matchedSkillCount", matched.size());

        List<Map<String, Object>> capabilityDimensions = buildCapabilityDimensions(missingInSchool, matched);
        result.put("capabilityDimensions", capabilityDimensions);
        result.put("jobFamilies", buildJobFamilies(major));
        result.put("graduationRequirements", buildGraduationRequirements(capabilityDimensions));
        result.put("curriculumActions", buildCurriculumActions(missingInSchool, redundantInSchool));
        result.put("sampleMeta", buildSampleMeta(courseKeywords.size(), marketSkillNames.size(), missingInSchool.size()));
        result.put("confidenceScore", confidenceScore(courseKeywords.size(), marketSkillNames.size()));
        result.put("summary", String.format(
                "课程关键词覆盖 %d 项，市场热门技能 %d 项，已匹配 %d 项，匹配率 %.1f%%。学校缺失技能 %d 项，校内低相关技能 %d 项。",
                courseKeywords.size(), marketSkillNames.size(), matched.size(), matchRate,
                missingInSchool.size(), redundantInSchool.size()
        ));

        return result;
    }

    public Map<String, Object> analyzeCurriculumGap(String major) {
        return analyzeSkyDemandGap(major);
    }

    private List<Map<String, Object>> buildCapabilityDimensions(List<Map<String, Object>> missingInSchool, List<String> matched) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        for (Map<String, Object> row : missingInSchool) {
            String skill = String.valueOf(row.get("skill"));
            String dimension = mapDimension(skill);
            dimensions.put(dimension, dimensions.getOrDefault(dimension, 0) + 1);
        }
        if (!matched.isEmpty()) {
            dimensions.put("已覆盖基础能力", matched.size());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dimensions.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dimension", entry.getKey());
            row.put("count", entry.getValue());
            row.put("priority", entry.getValue() >= 4 ? "P1" : entry.getValue() >= 2 ? "P2" : "P3");
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> buildJobFamilies(String major) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(job_classification, industry_name, '未分类') AS jobFamily, COUNT(*) AS demand, " +
                        "ROUND(AVG(COALESCE(salary_min, 0)), 2) AS avgSalary " +
                        "FROM biz_job_posting " +
                        "WHERE COALESCE(job_classification, industry_name) IS NOT NULL " +
                        "GROUP BY COALESCE(job_classification, industry_name) " +
                        "ORDER BY demand DESC LIMIT 8"
        );
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
            item.put("requirement", "毕业时应能够在" + row.get("dimension") + "方向独立完成岗位导向任务。");
            item.put("priority", row.get("priority"));
            result.add(item);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> buildCurriculumActions(List<Map<String, Object>> missingInSchool, List<String> redundantInSchool) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (!missingInSchool.isEmpty()) {
            actions.add(action("P1", "补齐高频能力点", "优先补齐" + firstSkills(missingInSchool, 4) + "相关课程任务、案例或实训。"));
        }
        if (!redundantInSchool.isEmpty()) {
            actions.add(action("P2", "复核低市场相关内容", "对" + String.join("、", redundantInSchool.subList(0, Math.min(4, redundantInSchool.size()))) + "等内容评估是否缩减学时。"));
        }
        actions.add(action("P1", "建立课程-能力点-岗位族矩阵", "让每门课都能回答支撑哪些能力点、服务哪些岗位族、形成哪些成果证据。"));
        actions.add(action("P2", "把结果纳入教改复盘", "按月复盘课程命中率、能力缺口和岗位需求变化。"));
        return actions;
    }

    private Map<String, Object> action(String priority, String title, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("priority", priority);
        row.put("title", title);
        row.put("detail", detail);
        return row;
    }

    private Map<String, Object> buildSampleMeta(int courseKeywordCount, int marketSkillCount, int missingCount) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("courseKeywordCount", courseKeywordCount);
        meta.put("marketSkillCount", marketSkillCount);
        meta.put("missingSkillCount", missingCount);
        meta.put("method", "keyword-matching-plus-dimension-bucketing");
        return meta;
    }

    private int confidenceScore(int courseKeywordCount, int marketSkillCount) {
        if (courseKeywordCount >= 40 && marketSkillCount >= 80) return 90;
        if (courseKeywordCount >= 20 && marketSkillCount >= 50) return 78;
        if (courseKeywordCount >= 10) return 66;
        return 50;
    }

    private String mapDimension(String skill) {
        String lower = skill == null ? "" : skill.toLowerCase();
        if (lower.contains("java") || lower.contains("python") || lower.contains("sql") || lower.contains("spring")) {
            return "工程开发能力";
        }
        if (lower.contains("analysis") || lower.contains("bi") || lower.contains("tableau") || lower.contains("power bi") || lower.contains("excel")) {
            return "数据分析与决策能力";
        }
        if (lower.contains("docker") || lower.contains("linux") || lower.contains("k8s") || lower.contains("运维")) {
            return "系统与云平台能力";
        }
        if (lower.contains("ai") || lower.contains("算法") || lower.contains("machine")) {
            return "算法与智能应用能力";
        }
        return "岗位综合能力";
    }

    private String firstSkills(List<Map<String, Object>> rows, int limit) {
        List<String> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object skill = row.get("skill");
            if (skill != null) {
                items.add(String.valueOf(skill));
            }
            if (items.size() >= limit) {
                break;
            }
        }
        return items.isEmpty() ? "核心能力" : String.join("、", items);
    }
}
