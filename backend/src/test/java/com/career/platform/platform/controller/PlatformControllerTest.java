package com.career.platform.platform.controller;

import com.career.platform.platform.service.UserInsightService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformControllerTest {

    private MockMvc mockMvc;
    private UserInsightService userInsightService;

    @BeforeEach
    void setUp() {
        userInsightService = mock(UserInsightService.class);
        PlatformController controller = new PlatformController(userInsightService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(9L, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void advisoryReturnsServicePayload() throws Exception {
        Map<String, Object> advisory = new LinkedHashMap<>();
        advisory.put("profileCompletenessScore", 85);
        advisory.put("actions", Collections.emptyList());
        when(userInsightService.buildPlatformAdvisory(9L)).thenReturn(advisory);

        mockMvc.perform(get("/api/v1/platform/advisory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.profileCompletenessScore").value(85));
    }
}
