package com.codejudge.platform.controller.admin;

import com.codejudge.platform.dto.SystemConfigResponse;
import com.codejudge.platform.dto.SystemConfigUpdateRequest;
import com.codejudge.platform.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSystemConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemConfigService systemConfigService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 管理员可以查询系统配置() throws Exception {
        when(systemConfigService.getConfig()).thenReturn(defaultResponse());

        mockMvc.perform(get("/api/admin/system-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ai.provider").value("DEEPSEEK"))
                .andExpect(jsonPath("$.data.ai.hasApiKey").value(false))
                .andExpect(jsonPath("$.data.judge.timeoutMs").value(3000));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void 学生无权访问系统配置() throws Exception {
        mockMvc.perform(get("/api/admin/system-config"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 未登录用户不能访问系统配置() throws Exception {
        mockMvc.perform(get("/api/admin/system-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 非法评测参数返回400() throws Exception {
        mockMvc.perform(put("/api/admin/system-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "judge": {
                                    "timeoutMs": 1,
                                    "memoryMb": 256,
                                    "maxConcurrent": 10
                                  },
                                  "ai": {
                                    "provider": "DEEPSEEK",
                                    "model": "deepseek-chat",
                                    "baseUrl": "https://api.deepseek.com",
                                    "apiKey": "",
                                    "clearApiKey": false
                                  },
                                  "limits": {
                                    "loginGlobal": 100,
                                    "loginPerUser": 10,
                                    "loginPerIp": 20,
                                    "aiGlobal": 300,
                                    "aiPerUser": 30,
                                    "aiPerIp": 100,
                                    "submitGlobal": 600,
                                    "submitPerUser": 60,
                                    "submitPerIp": 120
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 管理员可以一次性更新全部配置() throws Exception {
        when(systemConfigService.updateConfig(any(SystemConfigUpdateRequest.class)))
                .thenReturn(updatedResponse());

        mockMvc.perform(put("/api/admin/system-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "judge": {
                                    "timeoutMs": 3500,
                                    "memoryMb": 512,
                                    "maxConcurrent": 20
                                  },
                                  "ai": {
                                    "provider": "QWEN",
                                    "model": "qwen-plus",
                                    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                                    "apiKey": "",
                                    "clearApiKey": false
                                  },
                                  "limits": {
                                    "loginGlobal": 120,
                                    "loginPerUser": 12,
                                    "loginPerIp": 24,
                                    "aiGlobal": 360,
                                    "aiPerUser": 36,
                                    "aiPerIp": 120,
                                    "submitGlobal": 720,
                                    "submitPerUser": 72,
                                    "submitPerIp": 144
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.judge.timeoutMs").value(3500))
                .andExpect(jsonPath("$.data.judge.memoryMb").value(512))
                .andExpect(jsonPath("$.data.ai.provider").value("QWEN"))
                .andExpect(jsonPath("$.data.limits.loginPerUser").value(12));
    }

    private SystemConfigResponse defaultResponse() {
        return new SystemConfigResponse(
                new SystemConfigResponse.JudgeConfigResponse(3000, 256, 10),
                new SystemConfigResponse.AiConfigResponse(
                        "DEEPSEEK",
                        "deepseek-chat",
                        "https://api.deepseek.com",
                        false,
                        null),
                new SystemConfigResponse.LimitConfigResponse(
                        100, 10, 20, 300, 30, 100, 600, 60, 120),
                "admin",
                null);
    }

    private SystemConfigResponse updatedResponse() {
        return new SystemConfigResponse(
                new SystemConfigResponse.JudgeConfigResponse(3500, 512, 20),
                new SystemConfigResponse.AiConfigResponse(
                        "QWEN",
                        "qwen-plus",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        false,
                        null),
                new SystemConfigResponse.LimitConfigResponse(
                        120, 12, 24, 360, 36, 120, 720, 72, 144),
                "admin",
                null);
    }
}
