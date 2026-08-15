package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 学生数据访问接口（JPA，对应 MySQL students 表）。
 */
public interface StudentRepository extends JpaRepository<Student, Long> {

    /** 按登录账号查询学生 */
    Optional<Student> findByUsername(String username);
}
