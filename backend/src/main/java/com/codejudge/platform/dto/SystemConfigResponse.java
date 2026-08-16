package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 系统配置查询响应。
 *
 * <p>AI API Key 不返回完整明文，只返回是否已配置和掩码值。</p>
 */
public record SystemConfigResponse(
        JudgeConfigResponse judge,
        AiConfigResponse ai,
        LimitConfigResponse limits,
        String updatedBy,
        LocalDateTime updatedAt) {

    public record JudgeConfigResponse(
            int timeoutMs,
            int memoryMb,
            int maxConcurrent) {
    }

    public record AiConfigResponse(
            String provider,
            String model,
            String baseUrl,
            boolean hasApiKey,
            String maskedApiKey) {
    }

    public record LimitConfigResponse(
            int loginGlobal,
            int loginPerUser,
            int loginPerIp,
            int aiGlobal,
            int aiPerUser,
            int aiPerIp,
            int submitGlobal,
            int submitPerUser,
            int submitPerIp) {
    }
}
