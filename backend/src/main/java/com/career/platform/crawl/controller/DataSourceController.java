package com.career.platform.crawl.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.crawl.entity.DataSource;
import com.career.platform.crawl.mapper.DataSourceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Data Source Management", description = "Configure and monitor external recruitment data sources")
@RestController
@RequestMapping("/api/v1/crawl/sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DataSourceController {

    private final DataSourceMapper sourceMapper;

    @Operation(summary = "Data source list")
    @GetMapping
    public R<?> listSources() {
        List<DataSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<DataSource>().orderByAsc(DataSource::getId)
        );
        Map<String, Object> payload = new HashMap<>();
        payload.put("records", sources);
        payload.put("count", sources.size());
        return R.ok(payload);
    }

    @Operation(summary = "Data source detail")
    @GetMapping("/{id}")
    public R<DataSource> getSource(@PathVariable Long id) {
        DataSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw BusinessException.notFound("Data source not found");
        }
        return R.ok(source);
    }

    @Data
    public static class CreateSourceRequest {
        @NotBlank(message = "sourceName is required")
        private String sourceName;
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        private String baseUrl;
        private String crawlStrategy;
    }

    @Log("Create data source")
    @Operation(summary = "Create data source")
    @PostMapping
    public R<?> createSource(@Valid @RequestBody CreateSourceRequest req) {
        DataSource source = new DataSource();
        source.setSourceName(req.getSourceName());
        source.setSourceCode(req.getSourceCode());
        source.setBaseUrl(req.getBaseUrl());
        source.setCrawlStrategy(req.getCrawlStrategy());
        source.setIsActive(1);
        source.setTotalRecords(0L);
        source.setHealthStatus("UNKNOWN");
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        sourceMapper.insert(source);
        return R.ok("Data source created", source);
    }

    @Data
    public static class UpdateSourceRequest {
        private String sourceName;
        private String baseUrl;
        private String crawlStrategy;
        private Integer isActive;
        private String healthStatus;
        private Long totalRecords;
        private LocalDateTime lastCrawlAt;
    }

    @Log("Update data source")
    @Operation(summary = "Update data source")
    @PutMapping("/{id}")
    public R<?> updateSource(@PathVariable Long id, @RequestBody UpdateSourceRequest req) {
        DataSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw BusinessException.notFound("Data source not found");
        }

        if (req.getSourceName() != null) {
            source.setSourceName(req.getSourceName());
        }
        if (req.getBaseUrl() != null) {
            source.setBaseUrl(req.getBaseUrl());
        }
        if (req.getCrawlStrategy() != null) {
            source.setCrawlStrategy(req.getCrawlStrategy());
        }
        if (req.getIsActive() != null) {
            source.setIsActive(req.getIsActive());
        }
        if (req.getHealthStatus() != null) {
            source.setHealthStatus(req.getHealthStatus());
        }
        if (req.getTotalRecords() != null) {
            source.setTotalRecords(req.getTotalRecords());
        }
        if (req.getLastCrawlAt() != null) {
            source.setLastCrawlAt(req.getLastCrawlAt());
        }
        source.setUpdatedAt(LocalDateTime.now());
        sourceMapper.updateById(source);

        return R.ok("Data source updated", source);
    }

    @Log("Delete data source")
    @Operation(summary = "Delete data source")
    @DeleteMapping("/{id}")
    public R<?> deleteSource(@PathVariable Long id) {
        if (sourceMapper.selectById(id) == null) {
            throw BusinessException.notFound("Data source not found");
        }
        sourceMapper.deleteById(id);
        return R.ok("Data source deleted");
    }
}
