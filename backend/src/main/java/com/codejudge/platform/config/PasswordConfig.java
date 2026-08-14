package com.codejudge.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关配置。
 *
 * <p>提供 BCrypt 密码编码器，用于对用户密码做单向加密存储与校验。
 * 此处仅引入加密能力，完整的登录鉴权（Spring Security）尚未接入。</p>
 */
@Configuration
public class PasswordConfig {

    /** BCrypt 每次编码自动加盐，相同明文也会得到不同哈希 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
