package com.career.platform.config;

import com.career.platform.auth.filter.JwtAuthenticationFilter;
import com.career.platform.open.filter.ApiKeyAuthenticationFilter;
import com.career.platform.open.filter.RateLimitFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, RateLimitFilter rateLimitFilter,
                          ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.jwtFilter = jwtFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .headers()
                .contentSecurityPolicy("default-src 'self'; frame-ancestors 'none'; object-src 'none'")
                .and()
                .referrerPolicy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)
                .and()
                .frameOptions().deny()
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/captcha",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/password/reset/request",
                        "/api/v1/auth/password/reset/confirm"
                ).permitAll()
                .antMatchers(
                        "/doc.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/swagger-resources/**", "/webjars/**", "/swagger-ui.html"
                ).permitAll()
                .antMatchers("/health", "/actuator/**").permitAll()
                .antMatchers(HttpMethod.GET,
                        "/api/v1/open/**",
                        "/api/v1/jobs/**",
                        "/api/v1/analysis/**",
                        "/api/v1/reports/public",
                        "/api/v1/kg/skill-map",
                        "/api/v1/kg/job-skill-matrix",
                        "/api/v1/kg/career-ladder/**"
                ).permitAll()
                .antMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .antMatchers("/api/v1/crawl/**").hasRole("ADMIN")
                .antMatchers("/api/v1/curriculum/**").hasAnyRole("ADMIN", "TEACHER")
                .antMatchers("/api/v1/teacher/**").hasAnyRole("ADMIN", "TEACHER")
                .antMatchers("/api/v1/analysis/deep/**").hasAnyRole("ADMIN", "TEACHER")
                .antMatchers(HttpMethod.POST, "/api/v1/reports/generate").authenticated()
                .antMatchers(HttpMethod.POST, "/api/v1/reports/schedule").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports/*/status").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports/*/download").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports/*/pdf").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports/*/drill").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports/schedules").authenticated()
                .antMatchers(HttpMethod.GET, "/api/v1/reports/schedules/*").authenticated()
                .antMatchers(HttpMethod.PUT, "/api/v1/reports/schedules/*/toggle").authenticated()
                .antMatchers(HttpMethod.DELETE, "/api/v1/reports/schedules/*").authenticated()
                .anyRequest().authenticated()
                .and()
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition", "X-API-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
