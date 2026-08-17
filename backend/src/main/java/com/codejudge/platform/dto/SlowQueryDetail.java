package com.codejudge.platform.dto;

/**
 * MySQL 慢查询或高耗时语句详情。
 */
public record SlowQueryDetail(
        double durationSeconds,
        double lockTimeSeconds,
        long rowsExamined,
        long rowsSent,
        String sqlText) {
}
