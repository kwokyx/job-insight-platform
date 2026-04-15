package com.career.platform.crawl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据采集任务
 */
@Data
@TableName("crawl_task")
public class CrawlTask {

    @TableId(value = "task_id")
    private String taskId;

    private String parentTaskId;
    private String taskName;
    private String channel;
    private String keywords;
    private String city;
    private Integer status;
    private Integer priority;
    private Integer totalCount;
    private Integer finishedCount;
    private Integer duplicateCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createUser;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
