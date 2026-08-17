package com.codejudge.platform.dto;

/**
 * 数据库监控历史快照。
 */
public record DatabaseMonitorSnapshotResponse(
        Long id,
        DatabaseMonitorResponse snapshot) {
}
