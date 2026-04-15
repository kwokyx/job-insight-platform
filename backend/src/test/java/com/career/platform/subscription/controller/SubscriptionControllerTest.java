package com.career.platform.subscription.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.career.platform.common.exception.GlobalExceptionHandler;
import com.career.platform.subscription.entity.UserSubscription;
import com.career.platform.subscription.mapper.UserSubscriptionMapper;
import com.career.platform.subscription.service.PushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionControllerTest {

    private MockMvc mockMvc;
    private UserSubscriptionMapper subscriptionMapper;
    private PushService pushService;

    @BeforeEach
    void setUp() {
        subscriptionMapper = mock(UserSubscriptionMapper.class);
        pushService = mock(PushService.class);

        SubscriptionController controller = new SubscriptionController(subscriptionMapper, pushService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSubscriptionUsesCurrentUser() throws Exception {
        doAnswer(invocation -> {
            UserSubscription subscription = invocation.getArgument(0);
            subscription.setId(11L);
            return 1;
        }).when(subscriptionMapper).insert(any(UserSubscription.class));

        SubscriptionController.CreateSubscriptionRequest req = new SubscriptionController.CreateSubscriptionRequest();
        req.setSubscriptionType("JOB_PUSH");
        req.setFilterConfig("{\"city\":\"Shanghai\",\"skills\":[\"Java\"]}");
        req.setChannel("EMAIL");

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"));

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(subscriptionMapper).insert(captor.capture());
        assertEquals(Long.valueOf(7L), captor.getValue().getUserId());
        assertEquals("JOB_PUSH", captor.getValue().getSubscriptionType());
    }

    @Test
    void dispatchReturnsDeliveredCount() throws Exception {
        when(pushService.dispatchMatchesForSubscription(15L, 7L, 8)).thenReturn(3);

        mockMvc.perform(post("/api/v1/subscriptions/15/dispatch").param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deliveredCount").value(3));

        verify(pushService).dispatchMatchesForSubscription(15L, 7L, 8);
    }

    @Test
    void listReturnsCurrentUserSubscriptions() throws Exception {
        @SuppressWarnings("unchecked")
        IPage<UserSubscription> page = mock(IPage.class);
        UserSubscription subscription = new UserSubscription();
        subscription.setId(21L);
        subscription.setUserId(7L);

        when(subscriptionMapper.selectPage(any(), any())).thenReturn(page);
        when(page.getRecords()).thenReturn(Collections.singletonList(subscription));
        when(page.getTotal()).thenReturn(1L);

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.total").value(1));
    }
}
