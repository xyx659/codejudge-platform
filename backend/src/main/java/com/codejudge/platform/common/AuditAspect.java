package com.codejudge.platform.common;

import com.codejudge.platform.dto.LoginRequest;
import com.codejudge.platform.service.AuditEvent;
import com.codejudge.platform.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 业务操作审计切面。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "newpassword",
            "apikey",
            "api_key",
            "sourcecode",
            "token",
            "authorization",
            "accesskey",
            "secret");

    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public AuditAspect(ObjectMapper objectMapper, AuditLogService auditLogService) {
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(auditOperation)")
    public Object around(ProceedingJoinPoint joinPoint,
                         AuditOperation auditOperation) throws Throwable {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        HttpServletRequest request = currentRequest();
        String username = currentUsername();
        String userRole = currentRole();
        String requestParams = auditOperation.recordRequest()
                ? sanitize(joinPoint.getArgs())
                : null;
        if (username == null) {
            username = extractLoginUsername(joinPoint.getArgs());
        }

        boolean success = true;
        Integer httpStatus = 200;
        String errorMessage = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            success = false;
            httpStatus = statusFor(ex);
            errorMessage = truncate(ex.getMessage(), 250);
            throw ex;
        } finally {
            auditLogService.record(new AuditEvent(
                    traceId,
                    null,
                    username,
                    userRole,
                    request == null ? null : ClientIpUtil.resolve(request),
                    request == null ? null : request.getMethod(),
                    request == null ? null : request.getRequestURI(),
                    auditOperation.module(),
                    auditOperation.operation(),
                    auditOperation.description(),
                    requestParams,
                    success,
                    httpStatus,
                    errorMessage,
                    System.currentTimeMillis() - startTime));
        }
    }

    private String sanitize(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.node.ArrayNode root =
                    objectMapper.createArrayNode();
            for (Object arg : args) {
                if (shouldSkip(arg)) {
                    continue;
                }
                JsonNode node = objectMapper.valueToTree(arg);
                maskNode(node);
                root.add(node);
            }
            return truncate(root.toString(), 8000);
        } catch (Exception e) {
            return "{\"masked\":true}";
        }
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode value = objectNode.get(fieldName);
                if (isSensitive(fieldName)) {
                    objectNode.put(fieldName, "******");
                } else {
                    maskNode(value);
                }
            });
        } else if (node != null && node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    private boolean shouldSkip(Object arg) {
        return arg instanceof MultipartFile
                || arg instanceof HttpServletRequest
                || arg instanceof org.springframework.validation.BindingResult;
    }

    private boolean isSensitive(String fieldName) {
        String normalized = fieldName == null
                ? ""
                : fieldName.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELDS.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("apikey")
                || normalized.contains("api_key");
    }

    private String extractLoginUsername(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof LoginRequest loginRequest) {
                return loginRequest.username();
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String currentUsername() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private String currentRole() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    private int statusFor(Throwable ex) {
        if (ex instanceof BadRequestException) {
            return 400;
        }
        if (ex instanceof NotFoundException) {
            return 404;
        }
        if (ex instanceof RateLimitExceededException) {
            return 429;
        }
        if (ex instanceof org.springframework.security.core.AuthenticationException) {
            return 401;
        }
        return 500;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
