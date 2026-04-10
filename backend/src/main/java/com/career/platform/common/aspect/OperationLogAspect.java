package com.career.platform.common.aspect;

import com.career.platform.common.annotation.Log;
import com.career.platform.system.entity.OperationLog;
import com.career.platform.system.mapper.OperationLogMapper;
import com.career.platform.system.mapper.SysUserMapper;
import com.career.platform.system.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作日志 AOP 切面
 * 拦截带 @Log 注解的 Controller 方法，记录到 sys_operation_log
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper logMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    /** 简易用户名缓存（userId → username），避免每次查库 */
    private final Map<Long, String> usernameCache = new ConcurrentHashMap<>();

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint point, Log logAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();
        int responseCode = 200;
        Object result;

        try {
            result = point.proceed();
        } catch (Throwable e) {
            responseCode = 500;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            try {
                saveLog(point, logAnnotation.value(), responseCode, (int) duration);
            } catch (Exception e) {
                log.warn("保存操作日志失败: {}", e.getMessage());
            }
        }

        return result;
    }

    private void saveLog(ProceedingJoinPoint point, String operation, int responseCode, int duration) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;

        HttpServletRequest request = attributes.getRequest();

        OperationLog opLog = new OperationLog();

        // 用户信息 — 从 SecurityContext 获取 userId，再查缓存获取 username
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal())) {
            Long userId = (Long) auth.getPrincipal();
            opLog.setUserId(userId);
            opLog.setUsername(resolveUsername(userId));
        }

        // 方法信息
        MethodSignature sig = (MethodSignature) point.getSignature();
        opLog.setOperation(operation.isEmpty()
                ? sig.getDeclaringType().getSimpleName() + "." + sig.getName()
                : operation);
        opLog.setMethod(request.getMethod() + " " + sig.getDeclaringType().getSimpleName() + "." + sig.getName());
        opLog.setRequestUrl(request.getRequestURI());

        // 参数(截断防止过长)
        try {
            String params = objectMapper.writeValueAsString(point.getArgs());
            opLog.setRequestParams(params.length() > 1000 ? params.substring(0, 1000) : params);
        } catch (Exception ignored) {}

        opLog.setResponseCode(responseCode);
        opLog.setIpAddress(getClientIp(request));
        opLog.setUserAgent(request.getHeader("User-Agent"));
        opLog.setDurationMs(duration);
        opLog.setCreatedAt(LocalDateTime.now());

        logMapper.insert(opLog);
    }

    /**
     * 从缓存或数据库解析用户名
     */
    private String resolveUsername(Long userId) {
        return usernameCache.computeIfAbsent(userId, id -> {
            try {
                SysUser user = userMapper.selectById(id);
                return user != null ? user.getUsername() : "unknown";
            } catch (Exception e) {
                return "unknown";
            }
        });
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
