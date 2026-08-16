package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.SystemConfigKey;
import com.codejudge.platform.dto.SystemConfigResponse;
import com.codejudge.platform.dto.SystemConfigUpdateRequest;
import com.codejudge.platform.entity.SystemConfig;
import com.codejudge.platform.entity.SystemConfigAuditLog;
import com.codejudge.platform.repository.SystemConfigAuditLogRepository;
import com.codejudge.platform.repository.SystemConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    private static final String ENCRYPTION_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Mock
    private SystemConfigRepository configRepository;

    @Mock
    private SystemConfigAuditLogRepository auditLogRepository;

    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigService(
                configRepository,
                auditLogRepository,
                ENCRYPTION_KEY);
        lenient().when(configRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(configRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(auditLogRepository.save(any(SystemConfigAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 默认配置可以自动初始化() {
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        SystemConfigResponse response = systemConfigService.getConfig();

        assertEquals(3000, response.judge().timeoutMs(), "默认评测超时应为 3000 毫秒");
        assertEquals(256, response.judge().memoryMb(), "默认评测内存应为 256MB");
        assertEquals(10, response.judge().maxConcurrent(), "默认最大并发应为 10");
        assertEquals("DEEPSEEK", response.ai().provider(), "默认 AI 服务商应为 DeepSeek");
        assertFalse(response.ai().hasApiKey(), "默认 AI Key 应为未配置状态");
        assertEquals(100, response.limits().loginGlobal(), "默认登录全局限流应为 100");
    }

    @Test
    void 设置AIKey后数据库保存密文并返回掩码() {
        List<SystemConfig> configs = defaultConfigs();
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        String rawKey = "sk-secret-1234567890";
        SystemConfigResponse response = systemConfigService.updateConfig(
                updateRequest(rawKey, false));

        assertTrue(response.ai().hasApiKey(), "设置 Key 后应标记为已配置");
        assertEquals("****7890", response.ai().maskedApiKey(), "查询响应应只返回掩码");
        assertNotEquals(rawKey, response.ai().maskedApiKey(), "响应不应包含完整 Key");
        assertEquals(rawKey, systemConfigService.getAiRuntimeConfig().apiKey(),
                "内部 AI 配置应能解密并读取原 Key");
    }

    @Test
    void 清除AIKey后内部运行配置应返回空值() {
        List<SystemConfig> configs = defaultConfigs();
        configs.stream()
                .filter(config -> config.getConfigKey().equals(SystemConfigKey.AI_API_KEY.key()))
                .findFirst()
                .orElseThrow()
                .setConfigValue("encrypted-old-key");
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        SystemConfigResponse response = systemConfigService.updateConfig(
                updateRequest("", true));

        assertFalse(response.ai().hasApiKey(), "清除后应标记为未配置");
        assertNull(response.ai().maskedApiKey(), "清除后掩码应为空");
        assertNull(systemConfigService.getAiRuntimeConfig().apiKey(),
                "清除后内部 AI 配置不应返回 Key");
    }

    @Test
    void 普通配置更新后缓存立即刷新() {
        List<SystemConfig> configs = defaultConfigs();
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        assertEquals(3000, systemConfigService.getConfig().judge().timeoutMs(),
                "更新前超时应为默认值");

        SystemConfigResponse response = systemConfigService.updateConfig(
                updateRequest("", false));

        assertEquals(3500, response.judge().timeoutMs(),
                "更新后评测超时应为 3500 毫秒");
        assertEquals(3500, systemConfigService.getConfig().judge().timeoutMs(),
                "缓存刷新后再次读取应返回新值");
    }

    @Test
    void 普通配置审计记录旧值和新值() {
        List<SystemConfig> configs = defaultConfigs();
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        systemConfigService.updateConfig(updateRequest("", false));

        verify(auditLogRepository).save(argThat(log ->
                SystemConfigKey.JUDGE_TIMEOUT_MS.key().equals(log.getConfigKey())
                        && "3000".equals(log.getOldValue())
                        && "3500".equals(log.getNewValue())
                        && !log.isSensitive()));
    }

    @Test
    void 敏感配置审计不记录旧值和新值() {
        List<SystemConfig> configs = defaultConfigs();
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        systemConfigService.updateConfig(updateRequest("sk-new-key", false));

        verify(auditLogRepository).save(argThat(log ->
                SystemConfigKey.AI_API_KEY.key().equals(log.getConfigKey())
                        && log.getOldValue() == null
                        && log.getNewValue() == null
                        && log.isSensitive()));
    }

    @Test
    void 不支持的服务商应该拒绝() {
        List<SystemConfig> configs = defaultConfigs();
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest(
                judgeRequest(),
                aiRequest("INVALID_PROVIDER", "", false),
                limitRequest());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> systemConfigService.updateConfig(request),
                "非法 AI 服务商应该被拒绝");

        assertEquals("不支持的 AI 服务商", exception.getMessage(),
                "错误提示应说明服务商不合法");
    }

    @Test
    void 不能同时填写新Key和清除Key() {
        List<SystemConfig> configs = defaultConfigs();
        when(configRepository.findAllByOrderByIdAsc()).thenReturn(configs);
        setCurrentAdmin();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> systemConfigService.updateConfig(
                        updateRequest("sk-new-key", true)),
                "同时填写和清除 Key 应该被拒绝");

        assertEquals("不能同时填写 AI Key 和清除 AI Key", exception.getMessage(),
                "冲突提示应准确");
    }

    private List<SystemConfig> defaultConfigs() {
        return List.of(
                config(SystemConfigKey.JUDGE_TIMEOUT_MS, "3000"),
                config(SystemConfigKey.JUDGE_MEMORY_MB, "256"),
                config(SystemConfigKey.JUDGE_MAX_CONCURRENT, "10"),
                config(SystemConfigKey.AI_PROVIDER, "DEEPSEEK"),
                config(SystemConfigKey.AI_MODEL, "deepseek-chat"),
                config(SystemConfigKey.AI_BASE_URL, "https://api.deepseek.com"),
                config(SystemConfigKey.AI_API_KEY, ""),
                config(SystemConfigKey.LIMIT_LOGIN_GLOBAL, "100"),
                config(SystemConfigKey.LIMIT_LOGIN_PER_USER, "10"),
                config(SystemConfigKey.LIMIT_LOGIN_PER_IP, "20"),
                config(SystemConfigKey.LIMIT_AI_GLOBAL, "300"),
                config(SystemConfigKey.LIMIT_AI_PER_USER, "30"),
                config(SystemConfigKey.LIMIT_AI_PER_IP, "100"),
                config(SystemConfigKey.LIMIT_SUBMIT_GLOBAL, "600"),
                config(SystemConfigKey.LIMIT_SUBMIT_PER_USER, "60"),
                config(SystemConfigKey.LIMIT_SUBMIT_PER_IP, "120"));
    }

    private SystemConfig config(SystemConfigKey key, String value) {
        return new SystemConfig(
                key.key(),
                value,
                key.valueType(),
                key.encrypted(),
                null);
    }

    private SystemConfigUpdateRequest updateRequest(String apiKey, boolean clearKey) {
        return new SystemConfigUpdateRequest(
                judgeRequest(),
                aiRequest("DEEPSEEK", apiKey, clearKey),
                limitRequest());
    }

    private SystemConfigUpdateRequest.JudgeConfigUpdateRequest judgeRequest() {
        return new SystemConfigUpdateRequest.JudgeConfigUpdateRequest(3500, 256, 10);
    }

    private SystemConfigUpdateRequest.AiConfigUpdateRequest aiRequest(
            String provider,
            String apiKey,
            boolean clearKey) {
        return new SystemConfigUpdateRequest.AiConfigUpdateRequest(
                provider,
                "deepseek-chat",
                "https://api.deepseek.com",
                apiKey,
                clearKey);
    }

    private SystemConfigUpdateRequest.LimitConfigUpdateRequest limitRequest() {
        return new SystemConfigUpdateRequest.LimitConfigUpdateRequest(
                100, 10, 20, 300, 30, 100, 600, 60, 120);
    }

    private void setCurrentAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
