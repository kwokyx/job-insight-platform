package com.career.platform.open.controller;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.open.entity.ApiKey;
import com.career.platform.open.filter.ApiKeyAuthenticationFilter;
import com.career.platform.open.service.ApiKeyService;
import com.career.platform.open.service.OpenApiPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyAuthenticationFilterTest {

    private MockMvc mockMvc;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        OpenApiPermissionService openApiPermissionService = new OpenApiPermissionService(new ObjectMapper());
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService, openApiPermissionService, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new OpenPingController())
                .addFilters(filter)
                .build();
    }

    @Test
    void missingApiKeyAllowsOpenRequest() throws Exception {
        mockMvc.perform(get("/api/v1/open/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(header().string("X-Tenant-Scope", "public"));

        verify(apiKeyService, never()).validateAndTrack(any());
    }

    @Test
    void invalidApiKeyReturnsStructuredBusinessError() throws Exception {
        when(apiKeyService.validateAndTrack("bad-key"))
                .thenThrow(new BusinessException(401, "Invalid or disabled API Key"));

        mockMvc.perform(get("/api/v1/open/ping").header("X-API-Key", "bad-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or disabled API Key"));
    }

    @Test
    void validApiKeyAddsRateLimitHeadersAndTracksCall() throws Exception {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(9L);
        apiKey.setUserId(7L);
        apiKey.setDailyQuota(500);
        apiKey.setPermissions("{\"profile\":\"basic\",\"tenantScope\":\"campus-a\"}");

        when(apiKeyService.validateAndTrack("good-key")).thenReturn(apiKey);
        when(apiKeyService.getRemainingQuota(apiKey)).thenReturn(499);

        mockMvc.perform(get("/api/v1/open/ping").header("X-API-Key", "good-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "500"))
                .andExpect(header().string("X-RateLimit-Remaining", "499"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("X-Tenant-Scope", "campus-a"))
                .andExpect(jsonPath("$.data.status").value("ok"));

        verify(apiKeyService).recordApiCall(eq(apiKey), any(), any(), any(Long.class));
    }

    @RestController
    @RequestMapping("/api/v1/open")
    static class OpenPingController {

        @GetMapping("/ping")
        public Map<String, Object> ping() {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);

            Map<String, Object> data = new HashMap<>();
            data.put("status", "ok");
            result.put("data", data);
            return result;
        }
    }
}
