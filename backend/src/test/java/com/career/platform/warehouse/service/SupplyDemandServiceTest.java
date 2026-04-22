package com.career.platform.warehouse.service;

import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.MarketSkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplyDemandServiceTest {

    private JdbcTemplate jdbcTemplate;
    private JobPostingMapper jobPostingMapper;
    private SupplyDemandService supplyDemandService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        jobPostingMapper = mock(JobPostingMapper.class);

        MarketSkillService marketSkillService = new MarketSkillService(jobPostingMapper);
        supplyDemandService = new SupplyDemandService(jdbcTemplate, jobPostingMapper, marketSkillService);
    }

    @Test
    void analyzeCurriculumGapShouldReturnWeightedCoverageAndSyllabusAdjustments() {
        List<Map<String, Object>> curriculumRows = Arrays.asList(
                row("keyword", "Java"),
                row("keyword", "MySQL")
        );
        when(jdbcTemplate.queryForList(anyString())).thenReturn(curriculumRows);

        List<Map<String, Object>> marketRows = Arrays.asList(
                skill("Java", 120),
                skill("Docker", 90),
                skill("Redis", 70)
        );
        when(jobPostingMapper.topSkills(ArgumentMatchers.anyInt())).thenReturn(marketRows);
        when(jobPostingMapper.topSkillsByJobKeywords(ArgumentMatchers.anyList(), ArgumentMatchers.anyInt())).thenReturn(marketRows);

        when(jdbcTemplate.queryForList(anyString(), ArgumentMatchers.<Object[]>any()))
                .thenReturn(Collections.singletonList(jobFamily("后端开发", 188)));

        Map<String, Object> result = supplyDemandService.analyzeCurriculumGap(null);

        assertNotNull(result.get("weightedCoverageRate"));
        assertNotNull(result.get("algorithmMeta"));
        assertNotNull(result.get("dataQuality"));
        assertNotNull(result.get("healthFlags"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> missing = (List<Map<String, Object>>) result.get("missingInSchool");
        assertFalse(missing.isEmpty());
        assertTrue(String.valueOf(missing.get(0).get("skill")).length() > 0);
        assertTrue(String.valueOf(missing.get(0).get("moduleAdjustment")).length() > 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> syllabusAdjustments = (List<Map<String, Object>>) result.get("syllabusAdjustments");
        assertFalse(syllabusAdjustments.isEmpty());
        assertEquals("P1", String.valueOf(syllabusAdjustments.get(0).get("urgency")));
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private Map<String, Object> skill(String skill, int count) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("skill", skill);
        map.put("count", count);
        return map;
    }

    private Map<String, Object> jobFamily(String name, int demand) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jobFamily", name);
        map.put("demand", demand);
        map.put("avgSalary", 25);
        return map;
    }
}
