package com.career.platform.recommend.controller;

import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import com.career.platform.profile.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        jobPostingMapper = mock(JobPostingMapper.class);
        RecommendController controller = new RecommendController(
                jobPostingMapper,
                WebClient.builder().baseUrl("http://127.0.0.1:9").build(),
                mock(UserProfileMapper.class),
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(MarketSkillService.class),
                mock(UserInsightService.class)
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
                .andExpect(jsonPath("$.data.items[0].score").exists())
                .andExpect(jsonPath("$.data.items[0].title").value("Java Backend Engineer"))
                .andExpect(jsonPath("$.data.items[0].whyMatched").isArray());
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
