package com.codejudge.platform.controller;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.dto.LoginRequest;
import com.codejudge.platform.dto.LoginResponse;
import com.codejudge.platform.entity.User;
import com.codejudge.platform.repository.UserRepository;
import com.codejudge.platform.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录签发 JWT。
 *
 * <p>登录流程：校验用户名密码（BCrypt）→ 查用户取角色 → 签发 token 返回。
 * 密码错误时抛出 {@code BadCredentialsException}，由全局异常处理转为 401。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /** 登录，返回 JWT token */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 校验密码，失败抛出 AuthenticationException
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + request.username()));
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return ApiResponse.ok(new LoginResponse(token, user.getUsername(), user.getName(), user.getRole()));
    }
}
