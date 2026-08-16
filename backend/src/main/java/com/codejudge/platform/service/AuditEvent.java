package com.codejudge.platform.service;

/**
 * 异步写入审计表的事件对象。
 */
public record AuditEvent(
        String traceId,
        Long userId,
        String username,
        String userRole,
        String clientIp,
        String httpMethod,
        String requestUri,
        String module,
        String operation,
        String description,
        String requestParams,
        boolean success,
        Integer httpStatus,
        String errorMessage,
        long durationMs) {
}
