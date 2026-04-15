package com.career.platform.platform.controller;

import com.career.platform.common.result.R;
import com.career.platform.platform.service.UserInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform", description = "Cross-module advisory and platform orchestration APIs")
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final UserInsightService userInsightService;

    @Operation(summary = "Get personalized platform advisory")
    @GetMapping("/advisory")
    public R<?> advisory() {
        return R.ok(userInsightService.buildPlatformAdvisory(currentUserId()));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }
}
