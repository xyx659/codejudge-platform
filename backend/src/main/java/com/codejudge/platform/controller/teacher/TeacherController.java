package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.TeacherProfile;
import com.codejudge.platform.service.TeacherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教师个人信息接口（对外地址都以 {@code /api/teacher/profile} 开头）。
 *
 * <p>与管理员端个人信息保持一致：查看账号信息、修改姓名、修改密码。</p>
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    /** 获取当前教师个人信息 */
    @GetMapping("/profile")
    public ApiResponse<TeacherProfile> profile() {
        return ApiResponse.ok(teacherService.getProfile());
    }

    /** 修改当前教师姓名 */
    @PutMapping("/profile")
    public ApiResponse<TeacherProfile> updateProfile(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("姓名不能为空");
        }
        return ApiResponse.ok(teacherService.updateProfile(name));
    }

    /** 修改当前教师密码 */
    @PutMapping("/profile/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BadRequestException("原密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("新密码不能为空");
        }
        teacherService.changePassword(oldPassword, newPassword);
        return ApiResponse.ok(null);
    }
}
