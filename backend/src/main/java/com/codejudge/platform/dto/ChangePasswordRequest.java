package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改登录密码请求体。
 *
 * @param oldPassword 原密码（用于校验身份）
 * @param newPassword 新密码
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空") String newPassword) {
}
