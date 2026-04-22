package com.career.platform.ai.controller;

import com.career.platform.ai.client.LlmClient;
import com.career.platform.ai.entity.AiConversation;
import com.career.platform.ai.mapper.AiConversationMapper;
import com.career.platform.ai.mapper.AiMessageMapper;
import com.career.platform.ai.service.AiAgentService;
import com.career.platform.ai.service.AiFileImportService;
import com.career.platform.common.exception.GlobalExceptionHandler;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.platform.service.ReadinessService;
import com.career.platform.platform.service.UserInsightService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    private MockMvc mockMvc;
    private AiConversationMapper conversationMapper;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        LlmClient llmClient = mock(LlmClient.class);
        conversationMapper = mock(AiConversationMapper.class);
        AiMessageMapper messageMapper = mock(AiMessageMapper.class);
        JobPostingMapper jobPostingMapper = mock(JobPostingMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        AiFileImportService aiFileImportService = mock(AiFileImportService.class);
        UserInsightService userInsightService = mock(UserInsightService.class);
        ReadinessService readinessService = mock(ReadinessService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        valueOperations = ops;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AiController controller = new AiController(
                llmClient,
                conversationMapper,
                messageMapper,
                jobPostingMapper,
                redisTemplate,
                aiAgentService,
                aiFileImportService,
                objectMapper,
                userInsightService,
                readinessService,
                mock(java.util.concurrent.Executor.class)
        );
        ReflectionTestUtils.setField(controller, "dailyQuota", 20);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(5L, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listConversationsReturnsCurrentUserSessions() throws Exception {
        AiConversation conversation = new AiConversation();
        conversation.setId(1L);
        conversation.setSessionId("session-1");
        conversation.setTitle("Career Planning");
        conversation.setStatus(1);

        when(conversationMapper.selectList(any())).thenReturn(Collections.singletonList(conversation));

        mockMvc.perform(get("/api/v1/ai/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data[0].title").value("Career Planning"));
    }

    @Test
    void getQuotaFallsBackToZeroWhenRedisValueIsInvalid() throws Exception {
        when(valueOperations.get(any())).thenReturn("oops");

        mockMvc.perform(get("/api/v1/ai/quota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.used").value(0))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.remaining").value(20));
    }
}
