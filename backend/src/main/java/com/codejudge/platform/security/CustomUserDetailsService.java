package com.codejudge.platform.security;

import com.codejudge.platform.entity.User;
import com.codejudge.platform.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从数据库加载用户信息，供 Spring Security 登录时校验密码使用。
 *
 * <p>登录时由 {@code DaoAuthenticationProvider} 调用 {@link #loadUserByUsername}，
 * 拿到 BCrypt 哈希后与用户输入比对。角色通过 {@code roles(...)} 写入，
 * 自动加上 {@code ROLE_} 前缀。</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
