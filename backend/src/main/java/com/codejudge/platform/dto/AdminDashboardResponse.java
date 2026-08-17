package com.codejudge.platform.dto;

import java.util.List;

/**
 * 管理端工作台统计概览。
 */
public record AdminDashboardResponse(
        long studentCount,
        long teacherCount,
        long adminCount,
        long questionCount,
        long publishedQuestionCount,
        long submissionCount,
        long systemConfigCount,
        long auditLogCount,
        boolean mysqlOk,
        boolean mongoOk,
        List<AuditLogSummary> recentAuditLogs) {
}
