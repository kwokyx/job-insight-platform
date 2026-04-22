package com.career.platform.platform.service;

import com.career.platform.job.mapper.JobPostingMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MarketSkillServiceTest {

    @Test
    void similarityShouldRecognizeEquivalentSkillForms() {
        MarketSkillService service = new MarketSkillService(mock(JobPostingMapper.class));
        double score = service.skillSimilarity("SpringBoot", "spring boot");
        assertTrue(score >= 0.85D);
    }

    @Test
    void inferDimensionShouldMapToEngineering() {
        MarketSkillService service = new MarketSkillService(mock(JobPostingMapper.class));
        assertEquals("工程开发能力", service.inferCapabilityDimension("Java后端开发"));
    }
}
