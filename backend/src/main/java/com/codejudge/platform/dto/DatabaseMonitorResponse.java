package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 数据库监控完整状态。
 */
public record DatabaseMonitorResponse(
        MysqlStatusResponse mysql,
        MongoStatusResponse mongo,
        LocalDateTime collectedAt) {
}
