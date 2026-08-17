package com.codejudge.platform.dto;

/**
 * MySQL 数据表监控统计。
 */
public record DatabaseTableStat(
        String tableName,
        long rows,
        double dataLengthMb,
        double indexLengthMb) {
}
