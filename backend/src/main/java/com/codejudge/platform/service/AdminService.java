package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.AdminProfile;
import com.codejudge.platform.entity.Admin;
import com.codejudge.platform.repository.AdminRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理员业务逻辑（个人信息、修改密码等）。
 */
@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository adminRepository,
                        PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 获取当前管理员个人信息 */
    public AdminProfile getProfile() {
        Admin admin = currentAdmin();
        return new AdminProfile(
                admin.getId(),
                admin.getUsername(),
                admin.getName(),
                admin.getRole(),
                admin.getCreatedAt());
    }

    /** 修改当前管理员姓名 */
    public AdminProfile updateProfile(String name) {
        Admin admin = currentAdmin();
        admin.updateProfile(admin.getUsername(), name.trim());
        adminRepository.save(admin);
        return getProfile();
    }

    /** 修改当前管理员密码 */
    public void changePassword(String oldPassword, String newPassword) {
        Admin admin = currentAdmin();
        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            throw new BadRequestException("原密码错误");
        }
        admin.updatePassword(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);
    }

    private Admin currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("管理员不存在"));
    }
}
