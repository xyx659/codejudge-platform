package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 学生个人信息响应体。
 *
 * @param id        学生 ID
 * @param username  登录账号
 * @param name      姓名
 * @param role      角色，固定为 STUDENT
 * @param studentNo 学号，可空
 * @param className 班级，可空
 * @param createdAt 注册时间
 */
public record StudentProfile(
        Long id,
        String username,
        String name,
        String role,
        String studentNo,
        String className,
        LocalDateTime createdAt) {
}
