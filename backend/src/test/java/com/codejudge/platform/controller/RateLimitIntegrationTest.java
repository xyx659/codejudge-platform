package com.codejudge.platform.controller;

import com.codejudge.platform.dto.SystemConfigResponse;
import com.codejudge.platform.dto.SystemConfigUpdateRequest;
import com.codejudge.platform.security.JwtUtil;
import com.codejudge.platform.service.SystemConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void setAdminContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 登录接口超过单用户阈值返回429() throws Exception {
        configureLoginLimit(1);
        String username = "limit-login-" + UUID.randomUUID().toString().substring(0, 8);
        String ip = "198.51.100." + UUID.randomUUID().hashCode() % 250;

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 提交接口超过单用户阈值返回429() throws Exception {
        configureSubmitLimit(1);
        String username = "limit-submit-" + UUID.randomUUID().toString().substring(0, 8);
        String token = jwtUtil.generateToken(username, "STUDENT");

        mockMvc.perform(post("/api/student/submissions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/student/submissions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody()))
                .andExpect(status().isTooManyRequests());
    }

    private void configureLoginLimit(int loginPerUser) {
        SystemConfigResponse current = systemConfigService.getConfig();
        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest(
                new SystemConfigUpdateRequest.JudgeConfigUpdateRequest(
                        current.judge().timeoutMs(),
                        current.judge().memoryMb(),
                        current.judge().maxConcurrent()),
                new SystemConfigUpdateRequest.AiConfigUpdateRequest(
                        current.ai().provider(),
                        current.ai().model(),
                        current.ai().baseUrl(),
                        null,
                        false),
                new SystemConfigUpdateRequest.LimitConfigUpdateRequest(
                        current.limits().loginGlobal(),
                        loginPerUser,
                        current.limits().loginPerIp(),
                        current.limits().aiGlobal(),
                        current.limits().aiPerUser(),
                        current.limits().aiPerIp(),
                        current.limits().submitGlobal(),
                        current.limits().submitPerUser(),
                        current.limits().submitPerIp()));
        systemConfigService.updateConfig(request);
        SecurityContextHolder.clearContext();
    }

    private void configureSubmitLimit(int submitPerUser) {
        SystemConfigResponse current = systemConfigService.getConfig();
        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest(
                new SystemConfigUpdateRequest.JudgeConfigUpdateRequest(
                        current.judge().timeoutMs(),
                        current.judge().memoryMb(),
                        current.judge().maxConcurrent()),
                new SystemConfigUpdateRequest.AiConfigUpdateRequest(
                        current.ai().provider(),
                        current.ai().model(),
                        current.ai().baseUrl(),
                        null,
                        false),
                new SystemConfigUpdateRequest.LimitConfigUpdateRequest(
                        current.limits().loginGlobal(),
                        current.limits().loginPerUser(),
                        current.limits().loginPerIp(),
                        current.limits().aiGlobal(),
                        current.limits().aiPerUser(),
                        current.limits().aiPerIp(),
                        current.limits().submitGlobal(),
                        submitPerUser,
                        current.limits().submitPerIp()));
        systemConfigService.updateConfig(request);
        SecurityContextHolder.clearContext();
    }

    private String loginBody(String username) {
        return """
                {"username":"%s","password":"bad-password","role":"STUDENT"}
                """.formatted(username);
    }

    private String submitBody() {
        return """
                {"questionId":"integration-question","sourceCode":"public class Solution {}"}
                """;
    }
}
