package com.codejudge.platform.dto;

import java.util.List;

/**
 * MySQL 监控状态。
 */
public record MysqlStatusResponse(
        String status,
        String version,
        long uptimeSeconds,
        int maxConnections,
        int currentConnections,
        double connectionUsagePercent,
        double databaseSizeMb,
        long slowQueries,
        Long replicationDelayMs,
        double diskTotalMb,
        double diskFreeMb,
        List<DatabaseTableStat> tables,
        List<SlowQueryDetail> slowQueryDetails,
        String errorMessage) {
}
