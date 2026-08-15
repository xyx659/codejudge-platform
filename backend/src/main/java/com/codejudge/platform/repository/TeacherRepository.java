package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 教师数据访问接口（JPA，对应 MySQL teachers 表）。
 */
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /** 按登录账号查询教师 */
    Optional<Teacher> findByUsername(String username);
}
