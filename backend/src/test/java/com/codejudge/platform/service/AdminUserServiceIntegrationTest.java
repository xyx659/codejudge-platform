package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.AdminUserChangeRoleRequest;
import com.codejudge.platform.dto.AdminUserCreateRequest;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AdminUserServiceIntegrationTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 真实数据库中跨表用户名唯一性检查生效() {
        String username = uniqueUsername();
        studentRepository.save(student(username));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.createUser(
                        new AdminUserCreateRequest(
                                "TEACHER", username, "重复教师", "teacher123", null, null)),
                "学生表中已有用户名时，真实数据库测试应该拒绝创建教师");

        assertEquals("用户名已存在", exception.getMessage(), "跨表重复用户名的错误提示应统一");
        assertTrue(teacherRepository.findByUsername(username).isEmpty(),
                "重复用户名不应被写入教师表");
    }

    @Test
    void 真实数据库中无提交记录学生可以迁移为教师() {
        String username = uniqueUsername();
        Student student = studentRepository.save(student(username));

        var result = adminUserService.changeUserRole(
                "STUDENT",
                student.getId(),
                new AdminUserChangeRoleRequest("TEACHER"));

        assertEquals("TEACHER", result.role(), "迁移后的角色应为教师");
        assertTrue(teacherRepository.findByUsername(username).isPresent(),
                "迁移后教师表应包含该用户");
        assertTrue(studentRepository.findByUsername(username).isEmpty(),
                "迁移成功后原学生记录应被删除");
    }

    @Test
    void 真实数据库中有提交记录学生禁止迁移() {
        String username = uniqueUsername();
        Student student = studentRepository.save(student(username));
        submissionRepository.save(new Submission("integration-question", student.getId()));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.changeUserRole(
                        "STUDENT",
                        student.getId(),
                        new AdminUserChangeRoleRequest("TEACHER")),
                "有提交记录的学生在真实数据库测试中应被拒绝迁移");

        assertEquals("该学生已有提交记录，不能修改角色", exception.getMessage(),
                "提交记录限制的错误提示应准确");
        assertTrue(studentRepository.findByUsername(username).isPresent(),
                "迁移被拒绝后原学生记录必须保留");
        assertTrue(teacherRepository.findByUsername(username).isEmpty(),
                "迁移被拒绝后不应创建教师记录");
    }

    private String uniqueUsername() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Student student(String username) {
        Student student = new Student(
                username,
                "集成测试学生",
                passwordEncoder.encode("123456"));
        student.setStudentNo("IT" + username.substring(3).toUpperCase());
        return student;
    }
}
