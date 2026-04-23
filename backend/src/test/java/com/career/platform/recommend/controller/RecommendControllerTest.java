package com.career.platform.recommend.controller;

import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.profile.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendControllerTest {

    private MockMvc mockMvc;
    private JobPostingMapper jobPostingMapper;
    private MarketSkillService marketSkillService;

    @BeforeEach
    void setUp() {
        jobPostingMapper = mock(JobPostingMapper.class);
        marketSkillService = mock(MarketSkillService.class);
        when(marketSkillService.cleanSkillNames(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketSkillService.topSkills(anyInt())).thenReturn(Collections.emptyList());
        RecommendController controller = new RecommendController(
                jobPostingMapper,
                WebClient.builder().baseUrl("http://127.0.0.1:9").build(),
                mock(UserProfileMapper.class),
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                marketSkillService,
                mock(UserInsightService.class),
                mock(StringRedisTemplate.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void recommendJobsFallsBackToLocalScoring() throws Exception {
        JobPosting targetJob = new JobPosting();
        targetJob.setId(1L);
        targetJob.setTitle("Java Backend Engineer");
        targetJob.setCompanyName("Example Inc");
        targetJob.setCity("Shanghai");
        targetJob.setIndustryName("Internet");
        targetJob.setSalaryText("20K-30K");
        targetJob.setSalaryMin(new BigDecimal("20"));
        targetJob.setSalaryMax(new BigDecimal("30"));
        targetJob.setPublishDate(LocalDate.of(2026, 4, 10));

        JobPosting offTargetJob = new JobPosting();
        offTargetJob.setId(2L);
        offTargetJob.setTitle("Data Analyst");
        offTargetJob.setCompanyName("Example Analytics");
        offTargetJob.setCity("Shanghai");
        offTargetJob.setIndustryName("Internet");
        offTargetJob.setSalaryText("20K-30K");
        offTargetJob.setSalaryMin(new BigDecimal("20"));
        offTargetJob.setSalaryMax(new BigDecimal("30"));
        offTargetJob.setPublishDate(LocalDate.of(2026, 4, 10));

        when(jobPostingMapper.selectList(any())).thenReturn(Arrays.asList(targetJob, offTargetJob));
        when(jobPostingMapper.jobSkills(1L)).thenReturn(Arrays.asList("Java", "Spring Boot"));
        when(jobPostingMapper.jobSkills(2L)).thenReturn(Arrays.asList("Java", "SQL"));

        String payload = "{"
                + "\"targetJobType\":\"Backend Engineer\","
                + "\"skills\":[\"Java\"],"
                + "\"preferredCities\":[\"Shanghai\"],"
                + "\"industry\":\"Internet\","
                + "\"salaryMin\":15,"
                + "\"limit\":5"
                + "}";

        mockMvc.perform(post("/api/v1/recommend/jobs")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.summary").exists())
                .andExpect(jsonPath("$.data.profile").exists());
    }

    @Test
    void recommendJobsRelaxesStrictCoreSkillGateForStudentProfiles() throws Exception {
        JobPosting algorithmJob = new JobPosting();
        algorithmJob.setId(3L);
        algorithmJob.setTitle("大数据开发工程师");
        algorithmJob.setCompanyName("Example Data");
        algorithmJob.setCity("北京");
        algorithmJob.setIndustryName("人工智能");
        algorithmJob.setSalaryText("18K-28K");
        algorithmJob.setSalaryMin(new BigDecimal("18"));
        algorithmJob.setSalaryMax(new BigDecimal("28"));
        algorithmJob.setPublishDate(LocalDate.of(2026, 4, 10));

        when(jobPostingMapper.selectList(any())).thenReturn(Collections.singletonList(algorithmJob));
        when(jobPostingMapper.jobSkills(3L)).thenReturn(Arrays.asList("Python", "PySpark", "Hadoop"));

        String payload = "{"
                + "\"targetJobType\":\"算法工程师 / 大数据开发工程师\","
                + "\"skills\":[\"算法设计\",\"机器学习\",\"大模型本地部署\",\"C++\",\"Python\",\"PySpark\",\"Hadoop\"],"
                + "\"coreSkills\":[\"算法设计\",\"机器学习\",\"大模型本地部署\"],"
                + "\"preferredCities\":[\"北京\"],"
                + "\"industry\":\"人工智能\","
                + "\"limit\":5"
                + "}";

        mockMvc.perform(post("/api/v1/recommend/jobs")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].title").value("大数据开发工程师"));
    }

    @Test
    void skillRadarFallsBackWhenAlgorithmUnavailable() throws Exception {
        String payload = "{"
                + "\"userSkills\":[\"Java\",\"SQL\"],"
                + "\"targetJobType\":\"Backend\","
                + "\"city\":\"Shanghai\""
                + "}";

        mockMvc.perform(post("/api/v1/recommend/skill-radar")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.skills[0].skill").exists())
                .andExpect(jsonPath("$.data.targetJobType").value("Backend"));
    }

    @Test
    void rankerStatusFallsBackWhenAlgorithmUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/ranker-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.trained").value(false))
                .andExpect(jsonPath("$.data.model_type").value("unavailable"));
    }
}
