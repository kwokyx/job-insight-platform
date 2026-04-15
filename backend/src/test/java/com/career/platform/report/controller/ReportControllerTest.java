package com.career.platform.report.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.entity.ReportSchedule;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.mapper.ReportScheduleMapper;
import com.career.platform.report.service.ReportGenerationService;
import com.career.platform.platform.service.UserInsightService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

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
    private ReportScheduleMapper reportScheduleMapper;
    private ReportGenerationService reportGenerationService;

    @BeforeEach
    void setUp() {
        reportMapper = mock(AnalysisReportMapper.class);
        taskMapper = mock(AnalysisTaskMapper.class);
        reportScheduleMapper = mock(ReportScheduleMapper.class);
        reportGenerationService = mock(ReportGenerationService.class);

        ReportController controller = new ReportController(
                reportMapper,
                taskMapper,
                reportScheduleMapper,
                new ObjectMapper(),
                reportGenerationService,
                null,
                mock(UserInsightService.class)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, 2));
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

        String payload = "{"
                + "\"reportName\":\"Salary Report\","
                + "\"reportType\":\"SALARY\","
                + "\"params\":{\"city\":\"Shanghai\"}"
                + "}";

        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(101));

        verify(taskMapper).insert(any(AnalysisTask.class));
        verify(reportGenerationService).executeReportGeneration(101L, "SALARY", "Salary Report", 7L);
    }

    @Test
    void publicReportsReturnsPagedData() throws Exception {
        @SuppressWarnings("unchecked")
        IPage<AnalysisReport> page = mock(IPage.class);
        AnalysisReport report = new AnalysisReport();
        report.setId(5L);
        report.setReportName("Public Report");

        when(reportMapper.selectPage(any(), any())).thenReturn(page);
        when(page.getRecords()).thenReturn(Collections.singletonList(report));
        when(page.getTotal()).thenReturn(1L);

        mockMvc.perform(get("/api/v1/reports/public")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].reportName").value("Public Report"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void downloadReportIncrementsViewCount() throws Exception {
        AnalysisReport report = new AnalysisReport();
        report.setId(8L);
        report.setReportName("Download Report");
        report.setViewCount(3);
        report.setGeneratedBy(7L);

        when(reportMapper.selectById(8L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/reports/8/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.viewCount").value(4))
                .andExpect(jsonPath("$.data.reportName").value("Download Report"));

        verify(reportMapper).updateById(eq(report));
    }

    @Test
    void createScheduleStoresNextRunTime() throws Exception {
        String payload = "{"
                + "\"scheduleName\":\"Monthly Salary\","
                + "\"reportType\":\"SALARY\","
                + "\"cronExpr\":\"0 0 8 1 * ?\","
                + "\"params\":{\"city\":\"Shanghai\"}"
                + "}";

        doAnswer(invocation -> {
            ReportSchedule schedule = invocation.getArgument(0);
            schedule.setId(201L);
            return 1;
        }).when(reportScheduleMapper).insert(any(ReportSchedule.class));

        mockMvc.perform(post("/api/v1/reports/schedule")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(201))
                .andExpect(jsonPath("$.data.scheduleName").value("Monthly Salary"))
                .andExpect(jsonPath("$.data.createdBy").value(7));
    }

    @Test
    void listSchedulesReturnsOwnedSchedules() throws Exception {
        ReportSchedule schedule = new ReportSchedule();
        schedule.setId(33L);
        schedule.setScheduleName("Weekly Industry Report");
        schedule.setCreatedBy(7L);

        when(reportScheduleMapper.selectList(any())).thenReturn(Collections.singletonList(schedule));

        mockMvc.perform(get("/api/v1/reports/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(33))
                .andExpect(jsonPath("$.data[0].scheduleName").value("Weekly Industry Report"));
    }

    @Test
    void toggleScheduleFlipsActiveFlag() throws Exception {
        ReportSchedule schedule = new ReportSchedule();
        schedule.setId(45L);
        schedule.setCreatedBy(7L);
        schedule.setIsActive(1);
        schedule.setCronExpr("0 0 8 1 * ?");

        when(reportScheduleMapper.selectById(45L)).thenReturn(schedule);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/reports/schedules/45/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isActive").value(0));

        verify(reportScheduleMapper).updateById(eq(schedule));
    }
}
