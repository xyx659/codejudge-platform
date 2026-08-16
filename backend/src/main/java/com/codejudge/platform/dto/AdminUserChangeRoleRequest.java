package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端用户角色迁移请求体。
 *
 * @param targetRole 目标角色：ADMIN / TEACHER / STUDENT
 */
public record AdminUserChangeRoleRequest(
        @NotBlank(message = "目标角色不能为空") String targetRole) {
}
