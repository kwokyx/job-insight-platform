package com.career.platform.analysis.controller;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import com.career.platform.platform.service.UserInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
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
        AnalysisController controller = new AnalysisController(
                jobPostingMapper,
                mock(com.career.platform.common.util.RedisHelper.class),
                WebClient.builder().baseUrl("http://localhost:8000").build(),
                mock(UserInsightService.class),
                mock(MarketSkillService.class),
                mock(java.util.concurrent.Executor.class),
                null
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

    @Test
    void deepInsightsFallsBackToLocalStructure() throws Exception {
        Map<String, Object> city = new HashMap<>();
        city.put("city", "Shanghai");
        city.put("count", 120);
        city.put("avgSalary", 18.5);

        Map<String, Object> industry = new HashMap<>();
        industry.put("industry", "Artificial Intelligence");
        industry.put("count", 96);
        industry.put("avgSalary", 20.2);

        Map<String, Object> skill = new HashMap<>();
        skill.put("skill", "Python");
        skill.put("count", 80);

        Map<String, Object> january = new HashMap<>();
        january.put("period", "2026-01");
        january.put("avgSalaryMin", 12.0);
        january.put("avgSalaryMax", 22.0);
        january.put("jobCount", 40);

        Map<String, Object> february = new HashMap<>();
        february.put("period", "2026-02");
        february.put("avgSalaryMin", 13.0);
        february.put("avgSalaryMax", 24.0);
        february.put("jobCount", 50);

        Map<String, Object> march = new HashMap<>();
        march.put("period", "2026-03");
        march.put("avgSalaryMin", 14.0);
        march.put("avgSalaryMax", 25.0);
        march.put("jobCount", 60);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", 300);
        stats.put("avgSalaryMin", 13.0);
        stats.put("avgSalaryMax", 24.0);

        when(jobPostingMapper.aggregateByCity(anyInt())).thenReturn(Arrays.asList(city));
        when(jobPostingMapper.aggregateByIndustry(anyInt())).thenReturn(Arrays.asList(industry));
        when(jobPostingMapper.topSkills(anyInt())).thenReturn(Arrays.asList(skill));
        when(jobPostingMapper.salaryTrend(anyString(), anyString())).thenReturn(Arrays.asList(january, february, march));
        when(jobPostingMapper.overviewStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analysis/insights/deep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sample.totalJobs").value(300))
                .andExpect(jsonPath("$.data.marketPulse.salaryVolatility").exists())
                .andExpect(jsonPath("$.data.cityConcentration.topCity").value("Shanghai"))
                .andExpect(jsonPath("$.data.recommendations").isArray());
    }
}
