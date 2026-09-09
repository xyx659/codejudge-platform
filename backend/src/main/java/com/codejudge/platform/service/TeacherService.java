package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.TeacherProfile;
import com.codejudge.platform.entity.Teacher;
import com.codejudge.platform.repository.TeacherRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 教师业务逻辑（个人信息、修改密码等）。
 *
 * <p>与管理员端 {@link AdminService} 保持一致：查看账号信息、修改姓名、修改密码。</p>
 */
@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    public TeacherService(TeacherRepository teacherRepository,
                          PasswordEncoder passwordEncoder) {
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 获取当前教师个人信息 */
    public TeacherProfile getProfile() {
        Teacher teacher = currentTeacher();
        return new TeacherProfile(
                teacher.getId(),
                teacher.getUsername(),
                teacher.getName(),
                teacher.getRole(),
                teacher.getCreatedAt());
    }

    /** 修改当前教师姓名 */
    public TeacherProfile updateProfile(String name) {
        Teacher teacher = currentTeacher();
        teacher.updateProfile(teacher.getUsername(), name.trim());
        teacherRepository.save(teacher);
        return getProfile();
    }

    /** 修改当前教师密码 */
    public void changePassword(String oldPassword, String newPassword) {
        Teacher teacher = currentTeacher();
        if (!passwordEncoder.matches(oldPassword, teacher.getPassword())) {
            throw new BadRequestException("原密码错误");
        }
        teacher.updatePassword(passwordEncoder.encode(newPassword));
        teacherRepository.save(teacher);
    }

    private Teacher currentTeacher() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return teacherRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("教师不存在"));
    }
}
