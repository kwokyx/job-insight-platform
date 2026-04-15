package com.career.platform.crawl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.career.platform.crawl.entity.CrawlTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlTaskMapper extends BaseMapper<CrawlTask> {
}
