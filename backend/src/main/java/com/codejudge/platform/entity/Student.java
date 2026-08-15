package com.codejudge.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 学生实体，对应 MySQL 表 {@code students}。
 */
@Entity
@Table(name = "students")
public class Student extends User {

    /** 学号，仅学生使用，可空 */
    @Column(length = 20)
    private String studentNo;

    public Student() {
    }

    public Student(String username, String name, String password) {
        super(username, name, password);
    }

    @Override
    public String getRole() {
        return "STUDENT";
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }
}
