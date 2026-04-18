package com.career.platform.knowledgegraph.controller;

import com.career.platform.common.annotation.Log;
import com.career.platform.common.result.R;
import com.career.platform.knowledgegraph.service.KgBuildService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "知识图谱", description = "技能关系图谱、岗位技能矩阵、职业路径")
@RestController
@RequestMapping("/api/v1/kg")
public class KnowledgeGraphController {

    private final KgBuildService kgBuildService;
    private final JdbcTemplate jdbc;

    public KnowledgeGraphController(KgBuildService kgBuildService, JdbcTemplate jdbc) {
        this.kgBuildService = kgBuildService;
        this.jdbc = jdbc;
    }

    @Operation(summary = "技能关系图谱")
    @GetMapping("/skill-map")
    public R<?> skillMap(@RequestParam(defaultValue = "50") int topN) {
        return R.ok(kgBuildService.getSkillGraphData(Math.min(topN, 200)));
    }

    @Operation(summary = "岗位技能需求矩阵")
    @GetMapping("/job-skill-matrix")
    public R<?> jobSkillMatrix(
            @RequestParam(defaultValue = "10") int topJobs,
            @RequestParam(defaultValue = "15") int topSkills
    ) {
        return R.ok(kgBuildService.getJobSkillMatrix(
                Math.min(topJobs, 50), Math.min(topSkills, 50)
        ));
    }

    @Operation(summary = "职业晋升路径")
    @GetMapping("/career-ladder/{jobTitle}")
    public R<?> careerLadder(@PathVariable String jobTitle) {
        List<Map<String, Object>> paths = jdbc.queryForList(
                "SELECT job_title_from, job_title_to, transition_type, avg_years, required_skills, frequency " +
                        "FROM biz_career_path " +
                        "WHERE job_title_from LIKE ? OR job_title_to LIKE ? " +
                        "ORDER BY frequency DESC LIMIT 20",
                "%" + jobTitle + "%", "%" + jobTitle + "%"
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobTitle", jobTitle);
        payload.put("paths", paths);
        payload.put("total", paths.size());
        return R.ok(payload);
    }

    @Log("重建知识图谱")
    @Operation(summary = "管理员触发图谱重建")
    @PostMapping("/build")
    @PreAuthorize("hasRole('ADMIN')")
    public R<?> buildGraph(@RequestParam(defaultValue = "3") int minSupport) {
        return R.ok(kgBuildService.buildSkillCoOccurrence(minSupport));
    }
}
