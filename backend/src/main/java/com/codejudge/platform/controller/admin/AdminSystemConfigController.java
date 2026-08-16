package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.AuditOperation;
import com.codejudge.platform.dto.SystemConfigResponse;
import com.codejudge.platform.dto.SystemConfigUpdateRequest;
import com.codejudge.platform.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端系统配置接口。
 */
@RestController
@RequestMapping("/api/admin/system-config")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    public AdminSystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /** 查询当前系统配置，AI Key 只返回掩码 */
    @GetMapping
    public ApiResponse<SystemConfigResponse> getConfig() {
        return ApiResponse.ok(systemConfigService.getConfig());
    }

    /** 更新评测、AI 和限流配置 */
    @PutMapping
    @AuditOperation(
            module = "系统配置",
            operation = "UPDATE_CONFIG",
            description = "修改系统配置")
    public ApiResponse<SystemConfigResponse> updateConfig(
            @Valid @RequestBody SystemConfigUpdateRequest request) {
        return ApiResponse.ok(systemConfigService.updateConfig(request));
    }
}
