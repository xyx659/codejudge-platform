package com.codejudge.platform.service;

/**
 * AI 服务内部使用的运行时配置。
 *
 * <p>API Key 已解密，只允许后端内部服务读取，禁止写日志或返回前端。</p>
 */
public record AiRuntimeConfig(
        String provider,
        String model,
        String baseUrl,
        String apiKey) {
}
