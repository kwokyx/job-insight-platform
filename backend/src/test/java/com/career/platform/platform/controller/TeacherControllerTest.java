package com.career.platform.platform.controller;

import com.career.platform.platform.mapper.TeacherCourseMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.TeachingReformService;
import com.career.platform.warehouse.mapper.CurriculumMapper;
import com.career.platform.job.mapper.JobPostingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherControllerTest {

    private MockMvc mockMvc;
    private TeachingReformService teachingReformService;

    @BeforeEach
    void setUp() {
        teachingReformService = mock(TeachingReformService.class);
        TeacherController controller = new TeacherController(
                mock(TeacherCourseMapper.class),
                mock(JobPostingMapper.class),
                teachingReformService,
                mock(CurriculumMapper.class),
                mock(MarketSkillService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, 2));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teachingReformContractContainsGovernanceAndBlueprintFields() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("materialReadiness", Arrays.asList(
                material("课程清单", true, 24, "课程库可用于供需分析"),
                material("教学大纲", false, 0, "尚未上传")
        ));
        blueprint.put("courseActionPlans", Collections.singletonList(coursePlan()));
        response.put("blueprint", blueprint);
        response.put("governanceScorecard", governance());

        when(teachingReformService.buildTeachingReformAnalysis(7L, "数据科学与大数据技术"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/teacher/teaching-reform")
                        .param("major", "数据科学与大数据技术"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.blueprint.materialReadiness[0].name").value("课程清单"))
                .andExpect(jsonPath("$.data.blueprint.materialReadiness[0].ready").value(true))
                .andExpect(jsonPath("$.data.blueprint.materialReadiness[0].rowCount").value(24))
                .andExpect(jsonPath("$.data.blueprint.courseActionPlans[0].courseName").value("Python数据分析"))
                .andExpect(jsonPath("$.data.blueprint.courseActionPlans[0].priority").value("P1"))
                .andExpect(jsonPath("$.data.governanceScorecard.dimensions[0].label").value("课程资产"))
                .andExpect(jsonPath("$.data.governanceScorecard.risks[0]").value("教学大纲和学生情况资料仍偏弱，建议先补证据链。"));
    }

    private Map<String, Object> material(String name, boolean ready, int rowCount, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("ready", ready);
        item.put("rowCount", rowCount);
        item.put("detail", detail);
        return item;
    }

    private Map<String, Object> coursePlan() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("courseName", "Python数据分析");
        item.put("priority", "P1");
        item.put("riskScore", 72);
        item.put("missingSkills", Arrays.asList("数据可视化", "统计分析"));
        item.put("actions", Arrays.asList("补齐核心缺口技能", "增加项目式考核"));
        return item;
    }

    private Map<String, Object> governance() {
        Map<String, Object> scorecard = new LinkedHashMap<>();
        scorecard.put("summary", "治理得分可用于教学改革立项。");
        scorecard.put("dimensions", Collections.singletonList(dimension()));
        scorecard.put("risks", Collections.singletonList("教学大纲和学生情况资料仍偏弱，建议先补证据链。"));
        return scorecard;
    }

    private Map<String, Object> dimension() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", "课程资产");
        item.put("score", 66);
        item.put("evidence", "课程数 6 / 技能条目 18");
        return item;
    }
}
