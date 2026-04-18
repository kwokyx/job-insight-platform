package com.career.platform.job.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据时效性治理任务
 * 定期清理/标记过期的招聘数据，确保平台推荐和分析的准确性。
 */
@Component
public class JobDataGovernanceTask {

    private static final Logger log = LoggerFactory.getLogger(JobDataGovernanceTask.class);
    private final JobPostingMapper jobPostingMapper;

    public JobDataGovernanceTask(JobPostingMapper jobPostingMapper) {
        this.jobPostingMapper = jobPostingMapper;
    }

    /**
     * 每天凌晨 2 点执行：将抓取时间或发布时间超过 90 天的岗位标记为过期(status=0)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void markExpiredJobs() {
        log.info("开始执行数据时效性治理任务：标记过期岗位...");
        long startTime = System.currentTimeMillis();

        LocalDateTime thresholdDate = LocalDateTime.now().minusDays(90);

        LambdaUpdateWrapper<JobPosting> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(JobPosting::getStatus, 0)
                     .eq(JobPosting::getStatus, 1) // 只更新当前正常的
                     .and(w -> w
                             .lt(JobPosting::getCrawlTime, thresholdDate)
                             .or()
                             .lt(JobPosting::getPublishDate, thresholdDate.toLocalDate())
                     );

        int updatedCount = jobPostingMapper.update(null, updateWrapper);

        log.info("数据时效性治理任务完成。耗时: {} ms, 标记过期岗位数: {}", 
                (System.currentTimeMillis() - startTime), updatedCount);
    }
}
