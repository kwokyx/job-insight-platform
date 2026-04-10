package com.career.platform.open.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiControllerTest {

    private MockMvc mockMvc;
    private JobPostingMapper jobPostingMapper;

    @BeforeEach
    void setUp() {
        jobPostingMapper = mock(JobPostingMapper.class);
        OpenApiController controller = new OpenApiController(jobPostingMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void openJobsClampsPageSizeAndReturnsSlimFields() throws Exception {
        when(jobPostingMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<JobPosting> page = invocation.getArgument(0);
            JobPosting job = new JobPosting();
            job.setId(1L);
            job.setTitle("Java后端开发");
            job.setCompanyName("示例公司");
            job.setCity("上海");
            job.setIndustryName("互联网");
            job.setEducation("本科");
            job.setExperience("3年");
            job.setSalaryText("20K-30K");
            job.setPublishDate(LocalDate.of(2026, 4, 10));
            page.setRecords(List.of(job));
            page.setTotal(1);
            return page;
        });

        mockMvc.perform(get("/api/v1/open/jobs").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Java后端开发"))
                .andExpect(jsonPath("$.data[0].salaryText").value("20K-30K"));
    }

    @Test
    void openOverviewReturnsAggregateSummary() throws Exception {
        when(jobPostingMapper.overviewStats()).thenReturn(Map.of("avgSalaryMin", 12.0, "avgSalaryMax", 24.0));
        when(jobPostingMapper.selectCount(null)).thenReturn(100L);
        when(jobPostingMapper.aggregateByCity(10)).thenReturn(List.of(Map.of("city", "上海", "count", 40)));
        when(jobPostingMapper.aggregateByIndustry(10)).thenReturn(List.of(Map.of("industry", "互联网", "count", 60)));

        mockMvc.perform(get("/api/v1/open/analysis/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalJobs").value(100))
                .andExpect(jsonPath("$.data.topCities[0].city").value("上海"))
                .andExpect(jsonPath("$.data.topIndustries[0].industry").value("互联网"));
    }
}
