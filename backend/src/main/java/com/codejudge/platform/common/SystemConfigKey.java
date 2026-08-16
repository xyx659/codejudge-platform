package com.codejudge.platform.common;

import java.util.Arrays;

/**
 * 系统配置键枚举。
 *
 * <p>统一管理配置键、值类型和敏感标记，避免业务代码中散落字符串。</p>
 */
public enum SystemConfigKey {

    JUDGE_TIMEOUT_MS("judge.timeout_ms", "INT", false),
    JUDGE_MEMORY_MB("judge.memory_mb", "INT", false),
    JUDGE_MAX_CONCURRENT("judge.max_concurrent", "INT", false),

    AI_PROVIDER("ai.provider", "STRING", false),
    AI_MODEL("ai.model", "STRING", false),
    AI_BASE_URL("ai.base_url", "STRING", false),
    AI_API_KEY("ai.api_key", "ENCRYPTED", true),

    LIMIT_LOGIN_GLOBAL("limit.login.per_minute.global", "INT", false),
    LIMIT_LOGIN_PER_USER("limit.login.per_minute.per_user", "INT", false),
    LIMIT_LOGIN_PER_IP("limit.login.per_minute.per_ip", "INT", false),

    LIMIT_AI_GLOBAL("limit.ai.per_minute.global", "INT", false),
    LIMIT_AI_PER_USER("limit.ai.per_minute.per_user", "INT", false),
    LIMIT_AI_PER_IP("limit.ai.per_minute.per_ip", "INT", false),

    LIMIT_SUBMIT_GLOBAL("limit.submit.per_minute.global", "INT", false),
    LIMIT_SUBMIT_PER_USER("limit.submit.per_minute.per_user", "INT", false),
    LIMIT_SUBMIT_PER_IP("limit.submit.per_minute.per_ip", "INT", false);

    private final String key;
    private final String valueType;
    private final boolean encrypted;

    SystemConfigKey(String key, String valueType, boolean encrypted) {
        this.key = key;
        this.valueType = valueType;
        this.encrypted = encrypted;
    }

    public String key() {
        return key;
    }

    public String valueType() {
        return valueType;
    }

    public boolean encrypted() {
        return encrypted;
    }

    /** 根据数据库配置键查找枚举，未定义时抛出异常 */
    public static SystemConfigKey fromKey(String key) {
        return Arrays.stream(values())
                .filter(item -> item.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知系统配置键：" + key));
    }
}
