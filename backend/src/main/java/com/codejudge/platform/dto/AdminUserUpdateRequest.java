package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端修改用户请求体。
 *
 * <p>登录账号语义按角色区分：学生用学号（studentNo）作为账号，
 * 教师和管理员用工号作为账号，因此 studentNo 仅学生必填。</p>
 *
 * @param username  工号，仅教师/管理员使用（作为登录账号）
 * @param name      姓名
 * @param password  新密码，可空
 * @param studentNo 学号，仅学生使用（同时作为登录账号）
 * @param className 班级，仅学生使用（如「软件2502」）
 */
public record AdminUserUpdateRequest(
        @Size(max = 50, message = "工号不能超过 50 个字符") String username,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符") String name,
        @Size(min = 6, max = 100, message = "密码长度必须为 6 到 100 个字符") String password,
        @Size(max = 20, message = "学号不能超过 20 个字符") String studentNo,
        @Size(max = 50, message = "班级不能超过 50 个字符") String className) {
}
