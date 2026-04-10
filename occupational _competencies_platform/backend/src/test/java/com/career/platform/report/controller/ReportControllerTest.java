package com.career.platform.report.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.service.ReportGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private MockMvc mockMvc;
    private AnalysisReportMapper reportMapper;
    private AnalysisTaskMapper taskMapper;
    private ReportGenerationService reportGenerationService;

    @BeforeEach
    void setUp() {
        reportMapper = mock(AnalysisReportMapper.class);
        taskMapper = mock(AnalysisTaskMapper.class);
        reportGenerationService = mock(ReportGenerationService.class);

        ReportController controller = new ReportController(
                reportMapper,
                taskMapper,
                new ObjectMapper(),
                reportGenerationService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateReportCreatesTaskAndDelegatesToAsyncService() throws Exception {
        doAnswer(invocation -> {
            AnalysisTask task = invocation.getArgument(0);
            task.setId(101L);
            return 1;
        }).when(taskMapper).insert(any(AnalysisTask.class));

        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reportName": "薪资报告",
                                  "reportType": "SALARY",
                                  "params": {
                                    "city": "上海"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(101));

        verify(taskMapper).insert(any(AnalysisTask.class));
        verify(reportGenerationService).executeReportGeneration(101L, "SALARY", "薪资报告", 7L);
    }

    @Test
    void publicReportsReturnsPagedData() throws Exception {
        @SuppressWarnings("unchecked")
        IPage<AnalysisReport> page = mock(IPage.class);
        AnalysisReport report = new AnalysisReport();
        report.setId(5L);
        report.setReportName("公开报告");

        when(reportMapper.selectPage(any(), any())).thenReturn(page);
        when(page.getRecords()).thenReturn(List.of(report));
        when(page.getTotal()).thenReturn(1L);

        mockMvc.perform(get("/api/v1/reports/public")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].reportName").value("公开报告"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void downloadReportIncrementsViewCount() throws Exception {
        AnalysisReport report = new AnalysisReport();
        report.setId(8L);
        report.setReportName("下载报告");
        report.setViewCount(3);

        when(reportMapper.selectById(8L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/reports/8/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.viewCount").value(4))
                .andExpect(jsonPath("$.data.reportName").value("下载报告"));

        verify(reportMapper).updateById(eq(report));
    }
}
