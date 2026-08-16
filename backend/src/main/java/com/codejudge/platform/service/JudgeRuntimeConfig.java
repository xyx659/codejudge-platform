package com.codejudge.platform.service;

/**
 * JudgeService 运行时评测参数快照。
 */
public record JudgeRuntimeConfig(
        int timeoutMs,
        int memoryMb,
        int maxConcurrent) {
}
