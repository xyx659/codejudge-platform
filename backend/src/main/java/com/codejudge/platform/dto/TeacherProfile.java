package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 教师个人信息响应体。
 *
 * @param id        教师 ID
 * @param username  登录账号
 * @param name      姓名
 * @param role      角色，固定为 TEACHER
 * @param createdAt 注册时间
 */
public record TeacherProfile(
        Long id,
        String username,
        String name,
        String role,
        LocalDateTime createdAt) {
}
