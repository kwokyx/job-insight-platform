package com.career.platform.crawl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集日志明细
 */
@Data
@TableName("crawl_task_log")
public class CrawlLog {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private String taskId;
    private String shardId;
    private String workerId;
    private String level;
    private String message;

    private LocalDateTime createTime;
}
