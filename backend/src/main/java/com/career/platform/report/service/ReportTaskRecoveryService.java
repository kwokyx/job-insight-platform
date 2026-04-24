package com.career.platform.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.report.entity.AnalysisTask;
import com.career.platform.report.mapper.AnalysisReportMapper;
import com.career.platform.report.mapper.AnalysisTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportTaskRecoveryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportTaskRecoveryService.class);
    private static final String RECOVERY_MESSAGE = "报告服务重启导致任务中断，请重新生成。";

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisReportMapper analysisReportMapper;

    public ReportTaskRecoveryService(AnalysisTaskMapper analysisTaskMapper,
                                     AnalysisReportMapper analysisReportMapper) {
        this.analysisTaskMapper = analysisTaskMapper;
        this.analysisReportMapper = analysisReportMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AnalysisTask> runningTasks = analysisTaskMapper.selectList(
                new LambdaQueryWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getStatus, "RUNNING")
        );
        if (runningTasks.isEmpty()) {
            log.info("No interrupted report tasks found during startup recovery");
            return;
        }

        int recovered = 0;
        for (AnalysisTask task : runningTasks) {
            AnalysisReport report = analysisReportMapper.selectOne(
                    new LambdaQueryWrapper<AnalysisReport>()
                            .eq(AnalysisReport::getTaskId, task.getId())
                            .last("LIMIT 1")
            );
            if (report != null) {
                task.setStatus("SUCCESS");
                task.setProgress(100);
                task.setCompletedAt(report.getGeneratedAt() == null ? LocalDateTime.now() : report.getGeneratedAt());
                task.setErrorMessage(null);
            } else {
                task.setStatus("FAILED");
                task.setCompletedAt(LocalDateTime.now());
                task.setErrorMessage(RECOVERY_MESSAGE);
            }
            analysisTaskMapper.updateById(task);
            recovered++;
        }

        log.warn("Recovered {} interrupted report task(s) after startup", recovered);
    }
}
