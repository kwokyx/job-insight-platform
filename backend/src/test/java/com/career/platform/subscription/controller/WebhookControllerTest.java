package com.career.platform.subscription.controller;

import com.career.platform.common.exception.GlobalExceptionHandler;
import com.career.platform.subscription.entity.WebhookEndpoint;
import com.career.platform.subscription.mapper.WebhookDeliveryMapper;
import com.career.platform.subscription.mapper.WebhookEndpointMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private MockMvc mockMvc;
    private WebhookEndpointMapper webhookMapper;
    private WebhookDeliveryMapper deliveryMapper;

    @BeforeEach
    void setUp() {
        webhookMapper = mock(WebhookEndpointMapper.class);
        deliveryMapper = mock(WebhookDeliveryMapper.class);

        WebhookController controller = new WebhookController(webhookMapper, deliveryMapper);
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
    void listMasksWebhookSecret() throws Exception {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(5L);
        endpoint.setUserId(7L);
        endpoint.setEndpointUrl("https://example.com/webhook");
        endpoint.setSecretKey("1234567890abcdef");

        when(webhookMapper.selectList(any())).thenReturn(Collections.singletonList(endpoint));

        mockMvc.perform(get("/api/v1/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].secretKey").value("12345678****"));
    }

    @Test
    void createRegistersWebhookForCurrentUser() throws Exception {
        doAnswer(invocation -> {
            WebhookEndpoint endpoint = invocation.getArgument(0);
            endpoint.setId(18L);
            return 1;
        }).when(webhookMapper).insert(any(WebhookEndpoint.class));

        WebhookController.CreateWebhookRequest req = new WebhookController.CreateWebhookRequest();
        req.setEndpointUrl("https://example.com/webhook");
        req.setEventTypes("[\"JOB_PUSH\"]");

        mockMvc.perform(post("/api/v1/webhooks")
                        .contentType(APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.userId").value(7));

        ArgumentCaptor<WebhookEndpoint> captor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(webhookMapper).insert(captor.capture());
        assertEquals(Long.valueOf(7L), captor.getValue().getUserId());
        assertEquals(1, captor.getValue().getIsActive().intValue());
    }

    @Test
    void deliveriesRejectForeignWebhook() throws Exception {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(22L);
        endpoint.setUserId(9L);

        when(webhookMapper.selectById(22L)).thenReturn(endpoint);

        mockMvc.perform(get("/api/v1/webhooks/22/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Webhook not found"));
    }

    @Test
    void toggleFlipsWebhookStatus() throws Exception {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(30L);
        endpoint.setUserId(7L);
        endpoint.setIsActive(1);
        when(webhookMapper.selectById(30L)).thenReturn(endpoint);

        mockMvc.perform(put("/api/v1/webhooks/30/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(webhookMapper).updateById(endpoint);
        assertEquals(0, endpoint.getIsActive().intValue());
    }
}
