package com.codejudge.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 用户基类（不映射表），学生/教师/管理员实体继承它。
 *
 * <p>三个角色分表存储，公共字段（账号、姓名、密码、创建时间）定义在此，
 * 角色由具体子类通过 {@link #getRole()} 决定。</p>
 */
@MappedSuperclass
public abstract class User {

    private static final Logger log = LoggerFactory.getLogger(User.class);

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

    /** 创建时间，创建后不可更新 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {
    }

    public User(String username, String name, String password) {
        this.username = username;
        this.name = name;
        this.password = password;
    }

    /** 角色：由子类实现，返回 ADMIN / TEACHER / STUDENT */
    public abstract String getRole();

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 更新登录账号与姓名，创建时间和主键保持不变 */
    public void updateProfile(String username, String name) {
        log.debug("更新用户资料：id={}, role={}, username={}, name={}",
                getId(), getRole(), username, name);
        this.username = username;
        this.name = name;
    }

    /** 更新密码，调用方应传入 BCrypt 加密后的密码 */
    public void updatePassword(String password) {
        log.debug("更新用户密码：id={}, role={}", getId(), getRole());
        this.password = password;
    }

    /** 角色迁移时复制原记录的创建时间，保证新角色记录仍显示原注册时间 */
    public void copyCreatedAtFrom(User source) {
        this.createdAt = source.getCreatedAt();
    }
}
