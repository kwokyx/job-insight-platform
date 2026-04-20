package com.career.platform.auth.service;

import com.career.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptchaServiceTest {

    @Test
    void createCharCaptchaReturnsExpectedType() {
        CaptchaService service = new CaptchaService(null);
        Map<String, Object> captcha = service.createCaptcha("CHAR");

        assertEquals("CHAR", captcha.get("captchaType"));
        assertNotNull(captcha.get("captchaId"));
        assertNotNull(captcha.get("captchaPrompt"));
    }

    @Test
    void verifyRejectsWrongCode() {
        CaptchaService service = new CaptchaService(null);
        Map<String, Object> captcha = service.createCaptcha("MATH");

        assertThrows(BusinessException.class, () ->
                service.verify(String.valueOf(captcha.get("captchaId")), "wrong"));
    }
}
