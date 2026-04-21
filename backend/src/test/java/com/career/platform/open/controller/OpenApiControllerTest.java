package com.career.platform.open.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.open.service.OpenApiGovernanceService;
import com.career.platform.open.service.OpenApiPermissionService;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.service.SensitiveDataMaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private OpenApiGovernanceService openApiGovernanceService;
    private OpenApiPermissionService openApiPermissionService;

    @BeforeEach
    void setUp() {
        jobPostingMapper = mock(JobPostingMapper.class);
        reportMapper = mock(AnalysisReportMapper.class);
        openApiGovernanceService = mock(OpenApiGovernanceService.class);
        openApiPermissionService = new OpenApiPermissionService(new ObjectMapper());
        OpenApiController controller = new OpenApiController(
                jobPostingMapper,
                reportMapper,
                null,
                null,
                openApiGovernanceService,
                openApiPermissionService,
                new ObjectMapper(),
                new SensitiveDataMaskingService()
        );
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
            job.setJobLabels("Java,Spring");
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
                .andExpect(jsonPath("$.data[0].salaryText").value("20K-30K"))
                .andExpect(jsonPath("$.data[0].jobLabels").doesNotExist());
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

    @Test
    void subscriptionMetaReturnsContract() throws Exception {
        Map<String, Object> meta = new HashMap<>();
        meta.put("deliveryModes", Arrays.asList("scheduled-pull", "webhook"));
        meta.put("documentation", "docs/open-api-governance.md");

        when(openApiGovernanceService.buildSubscriptionMeta()).thenReturn(meta);

        mockMvc.perform(get("/api/v1/open/subscriptions/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deliveryModes[0]").value("scheduled-pull"))
                .andExpect(jsonPath("$.data.documentation").value("docs/open-api-governance.md"));
    }

    @Test
    void scopedPublicReportsOnlyReturnsTenantMatchedReports() throws Exception {
        AnalysisReport matched = new AnalysisReport();
        matched.setId(1L);
        matched.setReportName("Shanghai AI Report");
        matched.setReportType("INDUSTRY");
        matched.setDescription("matched");
        matched.setAnalysisData("{\"reportGovernance\":{\"cityFilter\":\"Shanghai\",\"industryFilter\":\"AI\"}}");
        matched.setGeneratedAt(LocalDateTime.of(2026, 4, 15, 10, 0));
        matched.setIsPublic(1);
        matched.setViewCount(8);

        AnalysisReport blocked = new AnalysisReport();
        blocked.setId(2L);
        blocked.setReportName("Beijing AI Report");
        blocked.setReportType("INDUSTRY");
        blocked.setDescription("blocked");
        blocked.setAnalysisData("{\"reportGovernance\":{\"cityFilter\":\"Beijing\",\"industryFilter\":\"AI\"}}");
        blocked.setGeneratedAt(LocalDateTime.of(2026, 4, 14, 10, 0));
        blocked.setIsPublic(1);
        blocked.setViewCount(5);

        when(reportMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<AnalysisReport> page = invocation.getArgument(0);
            page.setRecords(Arrays.asList(matched, blocked));
            page.setTotal(2);
            return page;
        });

        mockMvc.perform(get("/api/v1/open/reports/public-scoped")
                        .requestAttr("openApiTenantScope", "city:Shanghai")
                        .requestAttr("openApiAllowedReportFields", Arrays.asList("id", "reportName", "reportGovernance")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].reportName").value("Shanghai AI Report"))
                .andExpect(jsonPath("$.data[0].reportGovernance.cityFilter").value("Shanghai"));
    }

    @Test
    void publicReportDetailReturnsNotFoundWhenTenantScopeMismatches() throws Exception {
        AnalysisReport report = new AnalysisReport();
        report.setId(10L);
        report.setReportName("Beijing Governance Report");
        report.setReportType("INDUSTRY");
        report.setDescription("restricted");
        report.setAnalysisData("{\"reportGovernance\":{\"cityFilter\":\"Beijing\"}}");
        report.setIsPublic(1);

        when(reportMapper.selectById(10L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/open/reports/10")
                        .requestAttr("openApiTenantScope", "city:Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Report not found"));
    }
}
