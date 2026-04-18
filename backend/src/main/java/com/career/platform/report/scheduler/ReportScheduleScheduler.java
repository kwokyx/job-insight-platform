package com.career.platform.report.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.entity.ReportSchedule;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.mapper.ReportScheduleMapper;
import com.career.platform.report.service.ReportGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ReportScheduleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleScheduler.class);

    private final ReportScheduleMapper reportScheduleMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final ReportGenerationService reportGenerationService;

    public ReportScheduleScheduler(ReportScheduleMapper reportScheduleMapper, AnalysisTaskMapper analysisTaskMapper,
                                   ReportGenerationService reportGenerationService) {
        this.reportScheduleMapper = reportScheduleMapper;
        this.analysisTaskMapper = analysisTaskMapper;
        this.reportGenerationService = reportGenerationService;
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void dispatchDueSchedules() {
        LocalDateTime now = LocalDateTime.now();
        for (ReportSchedule schedule : reportScheduleMapper.selectList(
                new LambdaQueryWrapper<ReportSchedule>().eq(ReportSchedule::getIsActive, 1))) {
            if (schedule.getNextRunAt() == null || schedule.getNextRunAt().isAfter(now)) {
                continue;
            }

            try {
                AnalysisTask task = new AnalysisTask();
                task.setTaskName(schedule.getScheduleName());
                task.setTaskType(schedule.getReportType());
                task.setParams(schedule.getParams());
                task.setStatus("PENDING");
                task.setProgress(0);
                task.setCreatedBy(schedule.getCreatedBy());
                task.setCreatedAt(now);
                analysisTaskMapper.insert(task);
                reportGenerationService.executeReportGeneration(
                        task.getId(),
                        schedule.getReportType(),
                        schedule.getScheduleName(),
                        schedule.getCreatedBy()
                );

                schedule.setLastRunAt(now);
                schedule.setNextRunAt(CronExpression.parse(schedule.getCronExpr()).next(now));
                reportScheduleMapper.updateById(schedule);
            } catch (Exception ex) {
                log.warn("Failed to dispatch report schedule {}: {}", schedule.getId(), ex.getMessage());
            }
        }
    }
}
