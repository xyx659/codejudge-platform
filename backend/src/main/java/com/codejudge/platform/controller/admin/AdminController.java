package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.dto.AdminDashboardResponse;
import com.codejudge.platform.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口。
 *
 * <p>当前为骨架占位实现，仅返回状态提示，具体业务待补充。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    public AdminController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /** 管理端工作台真实统计概览 */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(adminDashboardService.getDashboard());
    }
}
