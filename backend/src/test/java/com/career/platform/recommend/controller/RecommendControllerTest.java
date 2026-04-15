package com.career.platform.recommend.controller;

import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.UserInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                mock(WebClient.class),
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                mock(UserInsightService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void recommendJobsFallsBackToLocalScoring() throws Exception {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setTitle("Java Engineer");
        job.setCompanyName("Example Inc");
        job.setCity("Shanghai");
        job.setIndustryName("Internet");
        job.setSalaryText("20K-30K");
        job.setSalaryMin(new BigDecimal("20"));
        job.setSalaryMax(new BigDecimal("30"));
        job.setPublishDate(LocalDate.of(2026, 4, 10));

        when(jobPostingMapper.selectList(any())).thenReturn(Collections.singletonList(job));
        when(jobPostingMapper.jobSkills(1L)).thenReturn(Arrays.asList("Java", "Spring Boot"));

        String payload = "{"
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
                .andExpect(jsonPath("$.data.source").value("local_fallback"))
                .andExpect(jsonPath("$.data.items[0].title").value("Java Engineer"))
                .andExpect(jsonPath("$.data.items[0].matchedSkills[0]").value("java"));
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
                .andExpect(jsonPath("$.data.source").value("local_fallback"))
                .andExpect(jsonPath("$.data.targetJobType").value("Backend"));
    }
}
