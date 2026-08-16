package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端修改用户请求体。
 *
 * <p>密码为空表示不修改密码，其余字段按请求值更新。</p>
 *
 * @param username  登录账号
 * @param name      姓名
 * @param password  新密码，可空
 * @param studentNo 学号，仅学生使用
 */
public record AdminUserUpdateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名不能超过 50 个字符") String username,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符") String name,
        @Size(min = 6, max = 100, message = "密码长度必须为 6 到 100 个字符") String password,
        @Size(max = 20, message = "学号不能超过 20 个字符") String studentNo) {
}
