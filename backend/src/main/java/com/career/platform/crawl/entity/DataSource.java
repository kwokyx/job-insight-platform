package com.career.platform.crawl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源配置
 */
@Data
@TableName("biz_data_source")
public class DataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceName;
    private String sourceCode;
    private String baseUrl;
    private String crawlStrategy;   // JSON
    private Integer isActive;
    private LocalDateTime lastCrawlAt;
    private Long totalRecords;
    private String healthStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
