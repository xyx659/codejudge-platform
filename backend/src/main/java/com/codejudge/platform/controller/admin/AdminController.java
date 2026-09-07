package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.dto.AdminDashboardResponse;
import com.codejudge.platform.dto.AdminProfile;
import com.codejudge.platform.service.AdminDashboardService;
import com.codejudge.platform.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AdminService adminService;

    public AdminController(AdminDashboardService adminDashboardService,
                           AdminService adminService) {
        this.adminDashboardService = adminDashboardService;
        this.adminService = adminService;
    }

    /** 管理端工作台真实统计概览 */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(adminDashboardService.getDashboard());
    }

    /** 获取当前管理员个人信息 */
    @GetMapping("/profile")
    public ApiResponse<AdminProfile> profile() {
        return ApiResponse.ok(adminService.getProfile());
    }

    /** 修改当前管理员姓名 */
    @PutMapping("/profile")
    public ApiResponse<AdminProfile> updateProfile(@RequestBody java.util.Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new com.codejudge.platform.common.BadRequestException("姓名不能为空");
        }
        return ApiResponse.ok(adminService.updateProfile(name));
    }

    /** 修改当前管理员密码 */
    @PutMapping("/profile/password")
    public ApiResponse<Void> changePassword(@RequestBody java.util.Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new com.codejudge.platform.common.BadRequestException("原密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new com.codejudge.platform.common.BadRequestException("新密码不能为空");
        }
        adminService.changePassword(oldPassword, newPassword);
        return ApiResponse.ok(null);
    }
}
