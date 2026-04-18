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
                rows = jdbc.queryForList(
                        baseQuery + " AND c.major LIKE ?",
                        "%" + major + "%"
                );
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
        result.put("summary", String.format(
                "课程关键词覆盖 %d 项，市场热门技能 %d 项，已匹配 %d 项，匹配率 %.1f%%。学校缺失技能 %d 项，学校冗余技能 %d 项。",
                courseKeywords.size(), marketSkillNames.size(), matched.size(), matchRate,
                missingInSchool.size(), redundantInSchool.size()
        ));

        return result;
    }

    public Map<String, Object> analyzeCurriculumGap(String major) {
        return analyzeSkyDemandGap(major);
    }
}
