package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.User;

import java.time.LocalDateTime;

/**
 * 管理端用户列表项。
 *
 * <p>三个角色分别存储在三张表，但管理端需要统一展示，
 * 所以用同一个 DTO 聚合学生、教师和管理员的公共信息。</p>
 *
 * @param id        用户 ID
 * @param username  登录账号
 * @param name      姓名
 * @param role      角色：ADMIN / TEACHER / STUDENT
 * @param studentNo 学号，仅学生有值，其他角色为 null
 * @param createdAt 创建时间
 */
public record AdminUserSummary(
        Long id,
        String username,
        String name,
        String role,
        String studentNo,
        LocalDateTime createdAt) {

    /** 把任意角色用户实体转换成管理端统一摘要 */
    public static AdminUserSummary from(User user) {
        String studentNo = user instanceof Student student ? student.getStudentNo() : null;
        return new AdminUserSummary(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole(),
                studentNo,
                user.getCreatedAt());
    }
}
