package com.codejudge.platform.dto;

import java.util.List;
import java.util.Map;

/**
 * MongoDB 监控状态。
 */
public record MongoStatusResponse(
        String status,
        String version,
        long uptimeSeconds,
        int currentConnections,
        double residentMemoryMb,
        double databaseSizeMb,
        double diskTotalMb,
        double diskFreeMb,
        List<DatabaseCollectionStat> collections,
        Map<String, Long> opcounters,
        String errorMessage) {
}
