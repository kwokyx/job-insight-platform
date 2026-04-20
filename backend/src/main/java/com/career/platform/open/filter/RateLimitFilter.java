package com.career.platform.open.filter;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.open.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/open/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-RateLimit-Policy", "daily-quota-and-token-bucket");
        String rawKey = request.getHeader(HEADER_API_KEY);
        if (!StringUtils.hasText(rawKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            apiKeyService.enforceBurstLimit(rawKey.trim());
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            writeError(response, ex.getCode(), ex.getMessage());
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("total", null);
        body.put("page", null);
        body.put("pageSize", null);
        body.put("timestamp", LocalDateTime.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
