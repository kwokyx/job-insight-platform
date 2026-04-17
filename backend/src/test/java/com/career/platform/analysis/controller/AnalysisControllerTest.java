package com.career.platform.analysis.controller;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerTest {

    private MockMvc mockMvc;
    private JobPostingMapper jobPostingMapper;

    @BeforeEach
    void setUp() {
        jobPostingMapper = mock(JobPostingMapper.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        AnalysisController controller = new AnalysisController(
                jobPostingMapper,
                redisTemplate,
                WebClient.builder().baseUrl("http://localhost:8000").build(),
                mock(UserInsightService.class),
                mock(MarketSkillService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void salaryTrendReturnsLineChartStructure() throws Exception {
        Map<String, Object> january = new HashMap<>();
        january.put("period", "2026-01");
        january.put("avgSalaryMin", 12.5);
        january.put("avgSalaryMax", 22.5);
        january.put("jobCount", 10);

        Map<String, Object> february = new HashMap<>();
        february.put("period", "2026-02");
        february.put("avgSalaryMin", 13.0);
        february.put("avgSalaryMax", 23.0);
        february.put("jobCount", 12);

        when(jobPostingMapper.salaryTrend(anyString(), anyString())).thenReturn(Arrays.asList(january, february));

        mockMvc.perform(get("/api/v1/analysis/salary/trend")
                        .param("city", "Shanghai")
                        .param("industry", "Internet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chartType").value("line"))
                .andExpect(jsonPath("$.data.xAxis[0]").value("2026-01"))
                .andExpect(jsonPath("$.data.series[0].name").value("avgSalaryMin"))
                .andExpect(jsonPath("$.data.filters.city").value("Shanghai"))
                .andExpect(jsonPath("$.data.data[1].jobCount").value(12));
    }
}
