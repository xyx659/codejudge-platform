package com.codejudge.platform.service;

import com.codejudge.platform.common.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private SystemConfigService systemConfigService;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(systemConfigService);
    }

    @Test
    void 登录限流超过单用户阈值后应拒绝() {
        when(systemConfigService.getRateLimitRuntimeConfig())
                .thenReturn(config(10, 1, 10));

        assertDoesNotThrow(
                () -> rateLimitService.checkLogin("student-a", "127.0.0.1"),
                "第一次登录应在限流范围内");
        assertThrows(
                RateLimitExceededException.class,
                () -> rateLimitService.checkLogin("student-a", "127.0.0.1"),
                "同一用户超过阈值后应返回限流异常");
    }

    @Test
    void 限流应区分单用户和单IP计数() {
        when(systemConfigService.getRateLimitRuntimeConfig())
                .thenReturn(config(10, 2, 2));

        assertDoesNotThrow(() -> rateLimitService.checkLogin("student-a", "10.0.0.1"));
        assertDoesNotThrow(() -> rateLimitService.checkLogin("student-b", "10.0.0.1"));
        assertThrows(
                RateLimitExceededException.class,
                () -> rateLimitService.checkLogin("student-c", "10.0.0.1"),
                "同一 IP 超过阈值后应拒绝，即使用户名不同");
    }

    @Test
    void 配置更新后限流服务应立即读取新阈值() {
        when(systemConfigService.getRateLimitRuntimeConfig())
                .thenReturn(config(10, 1, 10), config(10, 2, 10));

        assertDoesNotThrow(() -> rateLimitService.checkLogin("student-a", "127.0.0.1"));
        assertDoesNotThrow(
                () -> rateLimitService.checkLogin("student-a", "127.0.0.1"),
                "配置更新后第二次请求应使用新的单用户阈值");
        assertThrows(
                RateLimitExceededException.class,
                () -> rateLimitService.checkLogin("student-a", "127.0.0.1"),
                "达到更新后的阈值后应拒绝第三次请求");
    }

    @Test
    void 提交和AI限流使用各自独立计数() {
        when(systemConfigService.getRateLimitRuntimeConfig())
                .thenReturn(config(10, 1, 10));

        assertDoesNotThrow(() -> rateLimitService.checkSubmission("student-a", "127.0.0.1"));
        assertDoesNotThrow(() -> rateLimitService.checkAiCall("student-a", "127.0.0.1"));
        assertThrows(
                RateLimitExceededException.class,
                () -> rateLimitService.checkSubmission("student-a", "127.0.0.1"),
                "提交接口应使用自己的计数桶");
    }

    private RateLimitRuntimeConfig config(int loginGlobal, int loginUser, int loginIp) {
        return new RateLimitRuntimeConfig(
                loginGlobal,
                loginUser,
                loginIp,
                100,
                10,
                20,
                loginGlobal,
                loginUser,
                loginIp);
    }
}
