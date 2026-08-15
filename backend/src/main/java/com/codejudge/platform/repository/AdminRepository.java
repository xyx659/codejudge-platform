package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 管理员数据访问接口（JPA，对应 MySQL admins 表）。
 */
public interface AdminRepository extends JpaRepository<Admin, Long> {

    /** 按登录账号查询管理员 */
    Optional<Admin> findByUsername(String username);
}
