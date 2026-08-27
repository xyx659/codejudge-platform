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

    /** 班级，仅学生使用，可空（如「软件2502」） */
    @Column(length = 50)
    private String className;

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

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}
