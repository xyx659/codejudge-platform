package com.codejudge.platform.dto;

/**
 * MongoDB 集合监控统计。
 */
public record DatabaseCollectionStat(
        String collectionName,
        long count,
        double storageSizeMb) {
}
