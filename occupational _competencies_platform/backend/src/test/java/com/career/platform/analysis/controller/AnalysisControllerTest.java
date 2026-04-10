package com.career.platform.analysis.controller;

import com.career.platform.job.mapper.JobPostingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
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
        AnalysisController controller = new AnalysisController(jobPostingMapper, redisTemplate);
        ReflectionTestUtils.setField(controller, "algorithmServiceUrl", "http://localhost:8000");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void salaryTrendReturnsLineChartStructure() throws Exception {
        when(jobPostingMapper.salaryTrend(anyString(), anyString())).thenReturn(List.of(
                Map.of("period", "2026-01", "avgSalaryMin", 12.5, "avgSalaryMax", 22.5, "jobCount", 10),
                Map.of("period", "2026-02", "avgSalaryMin", 13.0, "avgSalaryMax", 23.0, "jobCount", 12)
        ));

        mockMvc.perform(get("/api/v1/analysis/salary/trend")
                        .param("city", "上海")
                        .param("industry", "互联网"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chartType").value("line"))
                .andExpect(jsonPath("$.data.xAxis[0]").value("2026-01"))
                .andExpect(jsonPath("$.data.series[0].name").value("avgSalaryMin"))
                .andExpect(jsonPath("$.data.filters.city").value("上海"))
                .andExpect(jsonPath("$.data.data[1].jobCount").value(12));
    }
}
