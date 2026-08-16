package com.codejudge.platform.service;

/**
 * 接口限流运行时配置，单位：次 / 分钟。
 */
public record RateLimitRuntimeConfig(
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
