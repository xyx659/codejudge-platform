package com.codejudge.platform.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 管理员实体，对应 MySQL 表 {@code admins}。
 */
@Entity
@Table(name = "admins")
public class Admin extends User {

    public Admin() {
    }

    public Admin(String username, String name, String password) {
        super(username, name, password);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}
