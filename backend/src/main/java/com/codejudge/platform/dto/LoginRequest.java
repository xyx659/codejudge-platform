package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体。
 *
 * @param username 登录账号
 * @param password 登录密码
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}
