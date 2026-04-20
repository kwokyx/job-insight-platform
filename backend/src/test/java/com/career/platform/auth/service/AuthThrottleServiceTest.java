package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthThrottleServiceTest {

    @Test
    void registerRateLimitBlocksAfterThreshold() {
        AuthThrottleService service = new AuthThrottleService(null);
        MockHttpServletRequest request = request("10.0.0.1", "JUnit");

        for (int i = 0; i < 6; i++) {
            service.onRegisterFailure(request);
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkRegisterAllowed(request));
        assertEquals(429, ex.getCode());
    }

    @Test
    void loginThrottleClearsAfterSuccess() {
        AuthThrottleService service = new AuthThrottleService(null);
        MockHttpServletRequest request = request("10.0.0.2", "JUnit");

        for (int i = 0; i < 5; i++) {
            service.onLoginFailure(request);
        }
        service.onLoginSuccess(request);

        assertDoesNotThrow(() -> service.checkLoginAllowed(request));
    }

    private MockHttpServletRequest request(String ip, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        request.addHeader("User-Agent", userAgent);
        return request;
    }
}
