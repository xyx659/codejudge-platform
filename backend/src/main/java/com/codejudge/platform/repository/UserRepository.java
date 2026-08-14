package com.codejudge.platform.repository;

import com.codejudge.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问接口（JPA，对应 MySQL users 表）。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按登录账号查询用户 */
    Optional<User> findByUsername(String username);
}
