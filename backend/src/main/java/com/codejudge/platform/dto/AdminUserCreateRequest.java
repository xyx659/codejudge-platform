package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端新增用户请求体。
 *
 * @param role      角色：ADMIN / TEACHER / STUDENT
 * @param username  登录账号
 * @param name      姓名
 * @param password  初始密码，服务层会进行 BCrypt 加密
 * @param studentNo 学号，仅学生使用
 */
public record AdminUserCreateRequest(
        @NotBlank(message = "角色不能为空") String role,
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名不能超过 50 个字符") String username,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符") String name,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度必须为 6 到 100 个字符") String password,
        @Size(max = 20, message = "学号不能超过 20 个字符") String studentNo) {
}
