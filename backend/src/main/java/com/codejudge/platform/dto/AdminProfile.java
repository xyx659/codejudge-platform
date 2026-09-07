package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 管理员个人信息响应体。
 *
 * @param id        管理员 ID
 * @param username  登录账号
 * @param name      姓名
 * @param role      角色，固定为 ADMIN
 * @param createdAt 注册时间
 */
public record AdminProfile(
        Long id,
        String username,
        String name,
        String role,
        LocalDateTime createdAt) {
}
