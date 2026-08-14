package com.codejudge.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 MySQL 表 {@code users}。
 *
 * <p>通过 {@code role} 区分三类账号：ADMIN（管理员）、TEACHER（教师）、STUDENT（学生）。</p>
 */
@Entity
@Table(name = "users")
public class User {

    /** 用户 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号，唯一 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 姓名 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 登录密码（BCrypt 加密存储） */
    @Column(nullable = false, length = 100)
    private String password;

    /** 角色：ADMIN / TEACHER / STUDENT */
    @Column(nullable = false, length = 20)
    private String role;

    /** 学号，仅学生使用，可空 */
    @Column(length = 20)
    private String studentNo;

    /** 创建时间，创建后不可更新 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {
    }

    public User(String username, String name, String password, String role) {
        this.username = username;
        this.name = name;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
