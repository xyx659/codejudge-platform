package com.codejudge.platform.dto;

import com.codejudge.platform.entity.OperationAuditLog;

import java.time.LocalDateTime;

/**
 * 管理端审计日志列表项。
 */
public record AuditLogSummary(
        Long id,
        String traceId,
        String username,
        String userRole,
        String clientIp,
        String httpMethod,
        String requestUri,
        String module,
        String operation,
        String description,
        boolean success,
        Integer httpStatus,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt) {

    public static AuditLogSummary from(OperationAuditLog log) {
        return new AuditLogSummary(
                log.getId(),
                log.getTraceId(),
                log.getUsername(),
                log.getUserRole(),
                log.getClientIp(),
                log.getHttpMethod(),
                log.getRequestUri(),
                log.getModule(),
                log.getOperation(),
                log.getDescription(),
                log.isSuccess(),
                log.getHttpStatus(),
                log.getErrorMessage(),
                log.getDurationMs(),
                log.getCreatedAt());
    }
}
