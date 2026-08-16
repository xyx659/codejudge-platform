package com.codejudge.platform.service;

import com.codejudge.platform.common.AiProvider;
import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.SystemConfigKey;
import com.codejudge.platform.dto.SystemConfigResponse;
import com.codejudge.platform.dto.SystemConfigUpdateRequest;
import com.codejudge.platform.entity.SystemConfig;
import com.codejudge.platform.entity.SystemConfigAuditLog;
import com.codejudge.platform.repository.SystemConfigAuditLogRepository;
import com.codejudge.platform.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 系统动态配置服务。
 *
 * <p>负责配置读取、类型转换、AI Key 加密、进程内缓存和修改审计。</p>
 */
@Service
public class SystemConfigService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final Map<SystemConfigKey, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(SystemConfigKey.JUDGE_TIMEOUT_MS, "3000"),
            Map.entry(SystemConfigKey.JUDGE_MEMORY_MB, "256"),
            Map.entry(SystemConfigKey.JUDGE_MAX_CONCURRENT, "10"),
            Map.entry(SystemConfigKey.AI_PROVIDER, "DEEPSEEK"),
            Map.entry(SystemConfigKey.AI_MODEL, "deepseek-chat"),
            Map.entry(SystemConfigKey.AI_BASE_URL, "https://api.deepseek.com"),
            Map.entry(SystemConfigKey.AI_API_KEY, ""),
            Map.entry(SystemConfigKey.LIMIT_LOGIN_GLOBAL, "100"),
            Map.entry(SystemConfigKey.LIMIT_LOGIN_PER_USER, "10"),
            Map.entry(SystemConfigKey.LIMIT_LOGIN_PER_IP, "20"),
            Map.entry(SystemConfigKey.LIMIT_AI_GLOBAL, "300"),
            Map.entry(SystemConfigKey.LIMIT_AI_PER_USER, "30"),
            Map.entry(SystemConfigKey.LIMIT_AI_PER_IP, "100"),
            Map.entry(SystemConfigKey.LIMIT_SUBMIT_GLOBAL, "600"),
            Map.entry(SystemConfigKey.LIMIT_SUBMIT_PER_USER, "60"),
            Map.entry(SystemConfigKey.LIMIT_SUBMIT_PER_IP, "120"));

    private final SystemConfigRepository configRepository;
    private final SystemConfigAuditLogRepository auditLogRepository;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    private volatile Map<String, SystemConfig> cache = Map.of();

    public SystemConfigService(SystemConfigRepository configRepository,
                               SystemConfigAuditLogRepository auditLogRepository,
                               @Value("${system.config.encryption-key}")
                               String encryptionKeyBase64) {
        this.configRepository = configRepository;
        this.auditLogRepository = auditLogRepository;
        this.encryptionKey = createEncryptionKey(encryptionKeyBase64);
    }

    @PostConstruct
    void initialize() {
        cache = loadConfigMap();
    }

    /** 查询当前配置，敏感字段只返回掩码 */
    public SystemConfigResponse getConfig() {
        return buildResponse(configMap());
    }

    /** 供评测服务读取当前评测参数 */
    public JudgeRuntimeConfig getJudgeRuntimeConfig() {
        Map<String, SystemConfig> configs = configMap();
        return new JudgeRuntimeConfig(
                intValue(configs, SystemConfigKey.JUDGE_TIMEOUT_MS),
                intValue(configs, SystemConfigKey.JUDGE_MEMORY_MB),
                intValue(configs, SystemConfigKey.JUDGE_MAX_CONCURRENT));
    }

    /** 仅供后端 AI 服务读取当前模型和已解密 Key */
    public AiRuntimeConfig getAiRuntimeConfig() {
        Map<String, SystemConfig> configs = configMap();
        String encryptedKey = require(configs, SystemConfigKey.AI_API_KEY).getConfigValue();
        return new AiRuntimeConfig(
                stringValue(configs, SystemConfigKey.AI_PROVIDER),
                stringValue(configs, SystemConfigKey.AI_MODEL),
                stringValue(configs, SystemConfigKey.AI_BASE_URL),
                hasText(encryptedKey) ? decrypt(encryptedKey) : null);
    }

    /** 供限流服务读取当前限流阈值 */
    public RateLimitRuntimeConfig getRateLimitRuntimeConfig() {
        Map<String, SystemConfig> configs = configMap();
        return new RateLimitRuntimeConfig(
                intValue(configs, SystemConfigKey.LIMIT_LOGIN_GLOBAL),
                intValue(configs, SystemConfigKey.LIMIT_LOGIN_PER_USER),
                intValue(configs, SystemConfigKey.LIMIT_LOGIN_PER_IP),
                intValue(configs, SystemConfigKey.LIMIT_AI_GLOBAL),
                intValue(configs, SystemConfigKey.LIMIT_AI_PER_USER),
                intValue(configs, SystemConfigKey.LIMIT_AI_PER_IP),
                intValue(configs, SystemConfigKey.LIMIT_SUBMIT_GLOBAL),
                intValue(configs, SystemConfigKey.LIMIT_SUBMIT_PER_USER),
                intValue(configs, SystemConfigKey.LIMIT_SUBMIT_PER_IP));
    }

    /** 更新评测、AI 和限流配置，并在同一事务中写入审计日志 */
    @Transactional
    public SystemConfigResponse updateConfig(SystemConfigUpdateRequest request) {
        String updatedBy = currentAdminUsername();
        Map<String, SystemConfig> configs = loadConfigMap();

        applyChange(configs, SystemConfigKey.JUDGE_TIMEOUT_MS,
                String.valueOf(request.judge().timeoutMs()), updatedBy);
        applyChange(configs, SystemConfigKey.JUDGE_MEMORY_MB,
                String.valueOf(request.judge().memoryMb()), updatedBy);
        applyChange(configs, SystemConfigKey.JUDGE_MAX_CONCURRENT,
                String.valueOf(request.judge().maxConcurrent()), updatedBy);

        String provider = normalizeProvider(request.ai().provider());
        applyChange(configs, SystemConfigKey.AI_PROVIDER, provider, updatedBy);
        applyChange(configs, SystemConfigKey.AI_MODEL,
                request.ai().model().trim(), updatedBy);
        applyChange(configs, SystemConfigKey.AI_BASE_URL,
                request.ai().baseUrl().trim(), updatedBy);
        applyAiKeyChange(configs, request, updatedBy);

        applyChange(configs, SystemConfigKey.LIMIT_LOGIN_GLOBAL,
                String.valueOf(request.limits().loginGlobal()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_LOGIN_PER_USER,
                String.valueOf(request.limits().loginPerUser()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_LOGIN_PER_IP,
                String.valueOf(request.limits().loginPerIp()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_AI_GLOBAL,
                String.valueOf(request.limits().aiGlobal()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_AI_PER_USER,
                String.valueOf(request.limits().aiPerUser()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_AI_PER_IP,
                String.valueOf(request.limits().aiPerIp()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_SUBMIT_GLOBAL,
                String.valueOf(request.limits().submitGlobal()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_SUBMIT_PER_USER,
                String.valueOf(request.limits().submitPerUser()), updatedBy);
        applyChange(configs, SystemConfigKey.LIMIT_SUBMIT_PER_IP,
                String.valueOf(request.limits().submitPerIp()), updatedBy);

        cache = loadConfigMap();
        return buildResponse(cache);
    }

    private void applyAiKeyChange(Map<String, SystemConfig> configs,
                                  SystemConfigUpdateRequest request,
                                  String updatedBy) {
        boolean clearKey = Boolean.TRUE.equals(request.ai().clearApiKey());
        boolean hasNewKey = hasText(request.ai().apiKey());
        if (clearKey && hasNewKey) {
            throw new BadRequestException("不能同时填写 AI Key 和清除 AI Key");
        }
        if (clearKey) {
            applyChange(configs, SystemConfigKey.AI_API_KEY, "", updatedBy);
        } else if (hasNewKey) {
            applyChange(configs, SystemConfigKey.AI_API_KEY,
                    encrypt(request.ai().apiKey().trim()), updatedBy);
        }
    }

    private void applyChange(Map<String, SystemConfig> configs,
                             SystemConfigKey configKey,
                             String newValue,
                             String updatedBy) {
        SystemConfig config = configs.get(configKey.key());
        if (config == null) {
            config = new SystemConfig(
                    configKey.key(),
                    DEFAULT_VALUES.get(configKey),
                    configKey.valueType(),
                    configKey.encrypted(),
                    null);
            configs.put(configKey.key(), config);
        }

        String oldValue = config.getConfigValue();
        if (Objects.equals(oldValue, newValue)) {
            return;
        }

        config.setConfigValue(newValue);
        config.setUpdatedBy(updatedBy);
        configRepository.save(config);

        boolean sensitive = configKey.encrypted();
        auditLogRepository.save(new SystemConfigAuditLog(
                configKey.key(),
                "UPDATE",
                sensitive ? null : oldValue,
                sensitive ? null : newValue,
                sensitive,
                updatedBy));
    }

    private SystemConfigResponse buildResponse(Map<String, SystemConfig> configs) {
        SystemConfig aiKeyConfig = require(configs, SystemConfigKey.AI_API_KEY);
        String encryptedApiKey = aiKeyConfig.getConfigValue();
        boolean hasApiKey = hasText(encryptedApiKey);
        String maskedApiKey = hasApiKey ? mask(decrypt(encryptedApiKey)) : null;

        SystemConfig latest = configs.values().stream()
                .max(Comparator.comparing(SystemConfig::getUpdatedAt))
                .orElse(null);

        return new SystemConfigResponse(
                new SystemConfigResponse.JudgeConfigResponse(
                        intValue(configs, SystemConfigKey.JUDGE_TIMEOUT_MS),
                        intValue(configs, SystemConfigKey.JUDGE_MEMORY_MB),
                        intValue(configs, SystemConfigKey.JUDGE_MAX_CONCURRENT)),
                new SystemConfigResponse.AiConfigResponse(
                        stringValue(configs, SystemConfigKey.AI_PROVIDER),
                        stringValue(configs, SystemConfigKey.AI_MODEL),
                        stringValue(configs, SystemConfigKey.AI_BASE_URL),
                        hasApiKey,
                        maskedApiKey),
                new SystemConfigResponse.LimitConfigResponse(
                        intValue(configs, SystemConfigKey.LIMIT_LOGIN_GLOBAL),
                        intValue(configs, SystemConfigKey.LIMIT_LOGIN_PER_USER),
                        intValue(configs, SystemConfigKey.LIMIT_LOGIN_PER_IP),
                        intValue(configs, SystemConfigKey.LIMIT_AI_GLOBAL),
                        intValue(configs, SystemConfigKey.LIMIT_AI_PER_USER),
                        intValue(configs, SystemConfigKey.LIMIT_AI_PER_IP),
                        intValue(configs, SystemConfigKey.LIMIT_SUBMIT_GLOBAL),
                        intValue(configs, SystemConfigKey.LIMIT_SUBMIT_PER_USER),
                        intValue(configs, SystemConfigKey.LIMIT_SUBMIT_PER_IP)),
                latest == null ? null : latest.getUpdatedBy(),
                latest == null ? null : latest.getUpdatedAt());
    }

    private Map<String, SystemConfig> configMap() {
        Map<String, SystemConfig> current = cache;
        if (current.isEmpty()) {
            current = loadConfigMap();
            cache = current;
        }
        return current;
    }

    private Map<String, SystemConfig> loadConfigMap() {
        Map<String, SystemConfig> result = new LinkedHashMap<>();
        List<SystemConfig> existing = configRepository.findAllByOrderByIdAsc();
        for (SystemConfig config : existing) {
            result.put(config.getConfigKey(), config);
        }

        List<SystemConfig> missing = DEFAULT_VALUES.entrySet().stream()
                .filter(entry -> !result.containsKey(entry.getKey().key()))
                .map(entry -> new SystemConfig(
                        entry.getKey().key(),
                        entry.getValue(),
                        entry.getKey().valueType(),
                        entry.getKey().encrypted(),
                        null))
                .toList();
        if (!missing.isEmpty()) {
            configRepository.saveAll(missing);
            missing.forEach(config -> result.put(config.getConfigKey(), config));
        }
        return Map.copyOf(result);
    }

    private String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new BadRequestException("AI Key 加密失败");
        }
    }

    private String decrypt(String encryptedText) {
        try {
            byte[] data = Base64.getDecoder().decode(encryptedText);
            if (data.length <= GCM_IV_LENGTH) {
                throw new BadRequestException("AI Key 配置格式错误");
            }
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(
                    cipher.doFinal(cipherText),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BadRequestException("AI Key 解密失败");
        }
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private SecretKeySpec createEncryptionKey(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("系统配置加密密钥长度必须为 16、24 或 32 字节");
        }
        return new SecretKeySpec(key, "AES");
    }

    private String normalizeProvider(String value) {
        try {
            return AiProvider.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("不支持的 AI 服务商");
        }
    }

    private int intValue(Map<String, SystemConfig> configs, SystemConfigKey key) {
        try {
            return Integer.parseInt(require(configs, key).getConfigValue());
        } catch (NumberFormatException e) {
            throw new BadRequestException("配置项 " + key.key() + " 的值格式错误");
        }
    }

    private String stringValue(Map<String, SystemConfig> configs, SystemConfigKey key) {
        return require(configs, key).getConfigValue();
    }

    private SystemConfig require(Map<String, SystemConfig> configs, SystemConfigKey key) {
        SystemConfig config = configs.get(key.key());
        if (config == null) {
            throw new BadRequestException("缺少系统配置：" + key.key());
        }
        return config;
    }

    private String currentAdminUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BadRequestException("无法识别当前管理员");
        }
        return authentication.getName();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
