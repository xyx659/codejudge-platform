package com.codejudge.platform.service;

import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.AuditLogSummary;
import com.codejudge.platform.entity.OperationAuditLog;
import com.codejudge.platform.repository.OperationAuditLogRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 业务操作审计日志服务。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final OperationAuditLogRepository auditLogRepository;

    public AuditLogService(OperationAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** 异步保存审计日志，不影响业务请求响应 */
    @Async("auditExecutor")
    public void record(AuditEvent event) {
        try {
            OperationAuditLog auditLog = toEntity(event);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("审计日志写入失败：operation={}, traceId={}",
                    event.operation(), event.traceId(), e);
        }
    }

    /** 管理员查询审计日志 */
    @Transactional(readOnly = true)
    public PageResult<AuditLogSummary> search(
            int page,
            int size,
            String username,
            String module,
            String operation,
            Boolean success,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        var result = auditLogRepository.findAll(
                buildSpecification(
                        username, module, operation, success, startTime, endTime),
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PageResult<>(
                result.getContent().stream()
                        .map(AuditLogSummary::from)
                        .toList(),
                safePage,
                safeSize,
                result.getTotalElements());
    }

    /** 按当前筛选条件导出 CSV，最多导出 5000 条 */
    @Transactional(readOnly = true)
    public String exportCsv(
            String username,
            String module,
            String operation,
            Boolean success,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        var logs = auditLogRepository.findAll(
                buildSpecification(
                        username, module, operation, success, startTime, endTime),
                PageRequest.of(
                        0,
                        5000,
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(
                writer,
                CSVFormat.DEFAULT.builder()
                        .setHeader(
                                "traceId",
                                "username",
                                "userRole",
                                "clientIp",
                                "httpMethod",
                                "requestUri",
                                "module",
                                "operation",
                                "description",
                                "success",
                                "httpStatus",
                                "errorMessage",
                                "durationMs",
                                "createdAt")
                        .build())) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            for (OperationAuditLog log : logs) {
                printer.printRecord(
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
                        log.getCreatedAt() == null
                                ? null
                                : log.getCreatedAt().format(formatter));
            }
        } catch (IOException e) {
            throw new IllegalStateException("审计日志 CSV 导出失败", e);
        }
        return writer.toString();
    }

    private Specification<OperationAuditLog> buildSpecification(
            String username,
            String module,
            String operation,
            Boolean success,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        Specification<OperationAuditLog> spec = Specification.where(null);
        if (hasText(username)) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("username")),
                            "%" + username.trim().toLowerCase(Locale.ROOT) + "%"));
        }
        if (hasText(module)) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("module"), module.trim()));
        }
        if (hasText(operation)) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("operation"), operation.trim()));
        }
        if (success != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("success"), success));
        }
        if (startTime != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
        }
        if (endTime != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
        }
        return spec;
    }

    private OperationAuditLog toEntity(AuditEvent event) {
        OperationAuditLog auditLog = new OperationAuditLog();
        auditLog.setTraceId(event.traceId());
        auditLog.setUserId(event.userId());
        auditLog.setUsername(event.username());
        auditLog.setUserRole(event.userRole());
        auditLog.setClientIp(event.clientIp());
        auditLog.setHttpMethod(event.httpMethod());
        auditLog.setRequestUri(event.requestUri());
        auditLog.setModule(event.module());
        auditLog.setOperation(event.operation());
        auditLog.setDescription(event.description());
        auditLog.setRequestParams(event.requestParams());
        auditLog.setSuccess(event.success());
        auditLog.setHttpStatus(event.httpStatus());
        auditLog.setErrorMessage(event.errorMessage());
        auditLog.setDurationMs(event.durationMs());
        return auditLog;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
