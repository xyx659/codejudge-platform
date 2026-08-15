package com.codejudge.platform.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 教师实体，对应 MySQL 表 {@code teachers}。
 */
@Entity
@Table(name = "teachers")
public class Teacher extends User {

    public Teacher() {
    }

    public Teacher(String username, String name, String password) {
        super(username, name, password);
    }

    @Override
    public String getRole() {
        return "TEACHER";
    }
}
