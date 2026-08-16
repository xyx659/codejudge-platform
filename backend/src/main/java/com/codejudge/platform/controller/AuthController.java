package com.codejudge.platform.controller;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.AuditOperation;
import com.codejudge.platform.common.ClientIpUtil;
import com.codejudge.platform.dto.LoginRequest;
import com.codejudge.platform.dto.LoginResponse;
import com.codejudge.platform.entity.User;
import com.codejudge.platform.repository.AdminRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.TeacherRepository;
import com.codejudge.platform.security.JwtUtil;
import com.codejudge.platform.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录签发 JWT。
 *
 * <p>三角色用户分表存储，登录时按 {@code role} 到对应表查用户并校验 BCrypt 密码。
 * 密码错误或账号不存在均抛出 {@link BadCredentialsException}，由全局异常处理转为 401。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AdminRepository adminRepository;
    private final RateLimitService rateLimitService;

    public AuthController(JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          AdminRepository adminRepository,
                          RateLimitService rateLimitService) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.adminRepository = adminRepository;
        this.rateLimitService = rateLimitService;
    }

    /** 登录，返回 JWT token */
    @PostMapping("/login")
    @AuditOperation(
            module = "认证",
            operation = "LOGIN",
            description = "用户登录",
            recordRequest = false)
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        rateLimitService.checkLogin(
                request.username(),
                ClientIpUtil.resolve(servletRequest));
        User user = findByRole(request.role(), request.username());
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return ApiResponse.ok(new LoginResponse(token, user.getUsername(), user.getName(), user.getRole()));
    }

    /** 根据角色到对应表查用户，查不到视为登录失败 */
    private User findByRole(String role, String username) {
        return switch (role) {
            case "STUDENT" -> studentRepository.findByUsername(username)
                    .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
            case "TEACHER" -> teacherRepository.findByUsername(username)
                    .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
            case "ADMIN" -> adminRepository.findByUsername(username)
                    .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
            default -> throw new BadCredentialsException("用户名或密码错误");
        };
    }
}
