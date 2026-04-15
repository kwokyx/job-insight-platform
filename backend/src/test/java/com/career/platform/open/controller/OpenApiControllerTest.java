package com.career.platform.open.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.mapper.AnalysisReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    private AnalysisReportMapper reportMapper;

    @BeforeEach
    void setUp() {
        jobPostingMapper = mock(JobPostingMapper.class);
        reportMapper = mock(AnalysisReportMapper.class);
        OpenApiController controller = new OpenApiController(jobPostingMapper, reportMapper, null, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void openJobsClampsPageSizeAndReturnsSlimFields() throws Exception {
        when(jobPostingMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<JobPosting> page = invocation.getArgument(0);
            JobPosting job = new JobPosting();
            job.setId(1L);
            job.setTitle("Java Backend Engineer");
            job.setCompanyName("Example Inc");
            job.setCity("Shanghai");
            job.setIndustryName("Internet");
            job.setEducation("Bachelor");
            job.setExperience("3 years");
            job.setSalaryText("20K-30K");
            job.setPublishDate(LocalDate.of(2026, 4, 10));
            page.setRecords(Collections.singletonList(job));
            page.setTotal(1);
            return page;
        });

        mockMvc.perform(get("/api/v1/open/jobs").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Java Backend Engineer"))
                .andExpect(jsonPath("$.data[0].salaryText").value("20K-30K"));
    }

    @Test
    void openOverviewReturnsAggregateSummary() throws Exception {
        Map<String, Object> overview = new HashMap<>();
        overview.put("avgSalaryMin", 12.0);
        overview.put("avgSalaryMax", 24.0);

        Map<String, Object> city = new HashMap<>();
        city.put("city", "Shanghai");
        city.put("count", 40);

        Map<String, Object> industry = new HashMap<>();
        industry.put("industry", "Internet");
        industry.put("count", 60);

        when(jobPostingMapper.overviewStats()).thenReturn(overview);
        when(jobPostingMapper.selectCount(null)).thenReturn(100L);
        when(jobPostingMapper.aggregateByCity(10)).thenReturn(Collections.singletonList(city));
        when(jobPostingMapper.aggregateByIndustry(10)).thenReturn(Collections.singletonList(industry));
        when(jobPostingMapper.topSkills(10)).thenReturn(Arrays.asList());

        mockMvc.perform(get("/api/v1/open/analysis/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalJobs").value(100))
                .andExpect(jsonPath("$.data.topCities[0].city").value("Shanghai"))
                .andExpect(jsonPath("$.data.topIndustries[0].industry").value("Internet"));
    }

    @Test
    void publicReportDetailReturnsVisibleReport() throws Exception {
        AnalysisReport report = new AnalysisReport();
        report.setId(9L);
        report.setReportName("Public Salary Report");
        report.setReportType("SALARY");
        report.setDescription("summary");
        report.setAnalysisData("{\"overview\":{}}");
        report.setIsPublic(1);
        report.setViewCount(6);

        when(reportMapper.selectById(9L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/open/reports/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.reportName").value("Public Salary Report"))
                .andExpect(jsonPath("$.data.viewCount").value(6));
    }
}
