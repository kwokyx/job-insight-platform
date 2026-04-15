package com.career.platform.report.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.entity.ReportSchedule;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import com.career.platform.report.mapper.ReportScheduleMapper;
import com.career.platform.report.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportScheduleScheduler {

    private final ReportScheduleMapper reportScheduleMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final ReportGenerationService reportGenerationService;

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
