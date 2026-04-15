package com.career.platform.knowledgegraph.service;

import com.career.platform.knowledgegraph.mapper.SkillRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class KgBuildService {

    private final JdbcTemplate jdbc;
    @SuppressWarnings("unused")
    private final SkillRelationMapper skillRelationMapper;

    @Transactional
    public Map<String, Object> buildSkillCoOccurrence(int minSupport) {
        log.info("Building skill co-occurrence graph, minSupport={}", minSupport);
        jdbc.execute("DELETE FROM biz_skill_relation WHERE relation_type = 'CO_OCCUR'");

        String sql = "INSERT INTO biz_skill_relation (skill_id_a, skill_id_b, relation_type, weight, created_at) " +
                "SELECT a.label_id, b.label_id, 'CO_OCCUR', COUNT(*), NOW() " +
                "FROM job_label_rel a " +
                "JOIN job_label_dict da ON a.label_id = da.id " +
                "JOIN job_label_rel b ON a.job_posting_id = b.job_posting_id AND a.label_id < b.label_id " +
                "JOIN job_label_dict db ON b.label_id = db.id " +
                "WHERE da.label_type IN ('skill', 'tool', 'language', 'framework') " +
                "  AND db.label_type IN ('skill', 'tool', 'language', 'framework') " +
                "GROUP BY a.label_id, b.label_id " +
                "HAVING COUNT(*) >= ? " +
                "ON DUPLICATE KEY UPDATE weight = VALUES(weight), created_at = NOW()";

        int rows = jdbc.update(sql, minSupport);
        log.info("Skill co-occurrence graph build completed, relations={}", rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relationsCount", rows);
        result.put("minSupport", minSupport);
        result.put("builtAt", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> getSkillGraphData(int topN) {
        List<Map<String, Object>> nodes = jdbc.queryForList(
                "SELECT d.id, d.label_name AS name, d.label_type AS category, " +
                        "(SELECT COUNT(*) FROM job_label_rel r WHERE r.label_id = d.id) AS value " +
                        "FROM job_label_dict d " +
                        "WHERE d.label_type IN ('skill', 'tool', 'language', 'framework') " +
                        "  AND d.id IN (SELECT DISTINCT skill_id_a FROM biz_skill_relation UNION SELECT DISTINCT skill_id_b FROM biz_skill_relation) " +
                        "ORDER BY value DESC LIMIT ?",
                topN
        );

        Set<Long> nodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            nodeIds.add(((Number) node.get("id")).longValue());
        }

        List<Map<String, Object>> links = new ArrayList<>();
        if (!nodeIds.isEmpty()) {
            List<Map<String, Object>> allLinks = jdbc.queryForList(
                    "SELECT sr.skill_id_a AS source, sr.skill_id_b AS target, sr.weight AS value, sr.relation_type AS type " +
                            "FROM biz_skill_relation sr ORDER BY sr.weight DESC LIMIT 500"
            );
            for (Map<String, Object> link : allLinks) {
                Long source = ((Number) link.get("source")).longValue();
                Long target = ((Number) link.get("target")).longValue();
                if (nodeIds.contains(source) && nodeIds.contains(target)) {
                    links.add(link);
                }
            }
        }

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("links", links);
        graph.put("totalNodes", nodes.size());
        graph.put("totalLinks", links.size());
        return graph;
    }

    public List<Map<String, Object>> getJobSkillMatrix(int topJobs, int topSkills) {
        return jdbc.queryForList(
                "SELECT j.title AS jobTitle, d.label_name AS skill, COUNT(*) AS demand " +
                        "FROM job_label_rel r " +
                        "JOIN biz_job_posting j ON r.job_posting_id = j.id " +
                        "JOIN job_label_dict d ON r.label_id = d.id " +
                        "WHERE d.label_type IN ('skill', 'tool', 'language', 'framework') " +
                        "  AND j.title IN (" +
                        "  SELECT title FROM biz_job_posting GROUP BY title ORDER BY COUNT(*) DESC LIMIT ?" +
                        ") AND d.id IN (" +
                        "  SELECT r2.label_id " +
                        "  FROM job_label_rel r2 " +
                        "  JOIN job_label_dict d2 ON r2.label_id = d2.id " +
                        "  WHERE d2.label_type IN ('skill', 'tool', 'language', 'framework') " +
                        "  GROUP BY r2.label_id ORDER BY COUNT(*) DESC LIMIT ?" +
                        ") " +
                        "GROUP BY j.title, d.label_name " +
                        "ORDER BY jobTitle, demand DESC",
                topJobs, topSkills
        );
    }
}
