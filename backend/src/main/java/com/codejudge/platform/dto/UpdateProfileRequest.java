package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改个人信息请求体（目前仅支持改姓名，账号 username 不允许自助修改）。
 *
 * @param name 新姓名
 */
public record UpdateProfileRequest(
        @NotBlank(message = "姓名不能为空") String name) {
}
