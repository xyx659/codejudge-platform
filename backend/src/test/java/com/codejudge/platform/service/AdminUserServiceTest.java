package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.AdminUserChangeRoleRequest;
import com.codejudge.platform.dto.AdminUserCreateRequest;
import com.codejudge.platform.dto.AdminUserSummary;
import com.codejudge.platform.entity.Admin;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Teacher;
import com.codejudge.platform.repository.AdminRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        adminUserService = new AdminUserService(
                studentRepository,
                teacherRepository,
                adminRepository,
                submissionRepository,
                passwordEncoder);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 新增学生时跨学生表检查学号重复() {
        String studentNo = "S1001";
        when(studentRepository.findByUsername(studentNo))
                .thenReturn(Optional.of(student(studentNo)));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.createUser(createRequest("STUDENT", studentNo)),
                "学生表中已有相同学号时应该拒绝新增");

        assertEquals("账号已存在", exception.getMessage(), "错误提示应与业务约定一致");
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void 新增学生时跨教师表检查学号重复() {
        String studentNo = "S1001";
        when(studentRepository.findByUsername(studentNo)).thenReturn(Optional.empty());
        when(teacherRepository.findByUsername(studentNo))
                .thenReturn(Optional.of(new Teacher(studentNo, "王老师", "hash")));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.createUser(createRequest("STUDENT", studentNo)),
                "教师表中已有相同学号时应该拒绝新增");

        assertEquals("账号已存在", exception.getMessage(), "跨表唯一性错误提示应统一");
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void 新增学生时跨管理员表检查学号重复() {
        String studentNo = "S1001";
        when(studentRepository.findByUsername(studentNo)).thenReturn(Optional.empty());
        when(teacherRepository.findByUsername(studentNo)).thenReturn(Optional.empty());
        when(adminRepository.findByUsername(studentNo))
                .thenReturn(Optional.of(new Admin(studentNo, "管理员", "hash")));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.createUser(createRequest("STUDENT", studentNo)),
                "管理员表中已有相同学号时应该拒绝新增");

        assertEquals("账号已存在", exception.getMessage(), "跨表唯一性错误提示应统一");
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void 新增用户时密码必须经过BCrypt加密() {
        String rawPassword = "123456";
        String studentNo = "P1001";
        whenNoUserExists(studentNo);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        when(studentRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserSummary result = adminUserService.createUser(
                new AdminUserCreateRequest(
                        "STUDENT", null, "密码测试", rawPassword, studentNo, "软件2501"));

        Student saved = captor.getValue();
        assertNotNull(saved.getPassword(), "保存的学生密码不能为空");
        assertNotEquals(rawPassword, saved.getPassword(), "数据库不能保存明文密码");
        assertTrue(passwordEncoder.matches(rawPassword, saved.getPassword()),
                "保存的密码必须能被 BCrypt 正确校验");
        assertEquals("STUDENT", result.role(), "创建结果的角色应为学生");
        assertEquals(studentNo, saved.getUsername(), "学生应以学号作为登录账号");
    }

    @Test
    void 非学生角色填写学号时应该拒绝() {
        String username = "teacher-with-student-no";

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.createUser(
                        new AdminUserCreateRequest(
                                "TEACHER", username, "李老师", "teacher123", "T0001", null)),
                "教师填写学号时应该拒绝");

        assertEquals("仅学生可以填写学号", exception.getMessage(), "错误提示应说明角色限制");
        verify(teacherRepository, never()).save(any(Teacher.class));
    }

    @Test
    void 非法角色应该拒绝新增() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.createUser(
                        new AdminUserCreateRequest(
                                "SUPERUSER", "bad-role", "非法角色", "123456", null, null)),
                "非法角色应该拒绝新增");

        assertEquals("角色不合法", exception.getMessage(), "错误提示应说明角色不合法");
    }

    @Test
    void 学生迁移为教师时先建新角色再删原角色() {
        Student source = student("move-student");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(submissionRepository.existsByStudentId(1L)).thenReturn(false);
        when(teacherRepository.findByUsername(source.getUsername())).thenReturn(Optional.empty());
        when(teacherRepository.save(any(Teacher.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserSummary result = adminUserService.changeUserRole(
                "STUDENT", 1L, new AdminUserChangeRoleRequest("TEACHER"));

        assertEquals("TEACHER", result.role(), "迁移后的角色应为教师");
        assertEquals(source.getUsername(), result.username(), "迁移时应保留用户名");

        InOrder order = inOrder(teacherRepository, studentRepository);
        order.verify(teacherRepository).save(any(Teacher.class));
        order.verify(studentRepository).delete(source);
    }

    @Test
    void 有提交记录的学生禁止角色迁移() {
        Student source = student("submission-student");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(submissionRepository.existsByStudentId(1L)).thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.changeUserRole(
                        "STUDENT", 1L, new AdminUserChangeRoleRequest("TEACHER")),
                "有提交记录的学生不应该允许角色迁移");

        assertEquals("该学生已有提交记录，不能修改角色", exception.getMessage(),
                "错误提示应明确指出提交记录限制");
        verify(teacherRepository, never()).save(any(Teacher.class));
        verify(studentRepository, never()).delete(any(Student.class));
    }

    @Test
    void 最后一个管理员禁止角色迁移() {
        Admin admin = new Admin("admin", "管理员", "hash");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.count()).thenReturn(1L);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.changeUserRole(
                        "ADMIN", 1L, new AdminUserChangeRoleRequest("TEACHER")),
                "系统最后一个管理员不允许迁移");

        assertEquals("系统至少需要保留一个管理员", exception.getMessage(),
                "错误提示应说明管理员保留规则");
    }

    @Test
    void 当前登录管理员禁止迁移自己的角色() {
        Admin admin = new Admin("admin", "管理员", "hash");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.count()).thenReturn(2L);
        setCurrentUsername("admin");

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.changeUserRole(
                        "ADMIN", 1L, new AdminUserChangeRoleRequest("TEACHER")),
                "当前登录管理员不应该迁移自己");

        assertEquals("不能修改当前登录管理员的角色", exception.getMessage(),
                "错误提示应说明不能修改自己");
    }

    @Test
    void 目标角色表已有同名用户时禁止迁移() {
        Teacher source = new Teacher("same-user", "王老师", "hash");
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(source));
        when(studentRepository.findByUsername("same-user"))
                .thenReturn(Optional.of(student("same-user")));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.changeUserRole(
                        "TEACHER", 1L, new AdminUserChangeRoleRequest("STUDENT")),
                "目标角色表已有同名用户时不允许迁移");

        assertEquals("目标角色中已存在相同用户名", exception.getMessage(),
                "错误提示应说明目标角色重复");
        verify(studentRepository, never()).save(any(Student.class));
        verify(teacherRepository, never()).delete(any(Teacher.class));
    }

    @Test
    void 有提交记录的学生禁止删除() {
        Student source = student("delete-student");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(submissionRepository.existsByStudentId(1L)).thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.deleteUser("STUDENT", 1L),
                "有提交记录的学生不允许删除");

        assertEquals("该学生已有提交记录，不能删除", exception.getMessage(),
                "错误提示应说明删除限制");
        verify(studentRepository, never()).delete(source);
    }

    @Test
    void 最后一个管理员禁止删除() {
        Admin admin = new Admin("admin", "管理员", "hash");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.count()).thenReturn(1L);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminUserService.deleteUser("ADMIN", 1L),
                "系统最后一个管理员不允许删除");

        assertEquals("系统至少需要保留一个管理员", exception.getMessage(),
                "错误提示应说明管理员保留规则");
        verify(adminRepository, never()).delete(admin);
    }

    private AdminUserCreateRequest createRequest(String role, String username) {
        return new AdminUserCreateRequest(
                role, username, "测试用户", "123456",
                "STUDENT".equals(role) ? "S1001" : null,
                "STUDENT".equals(role) ? "软件2501" : null);
    }

    private Student student(String username) {
        Student student = new Student(username, "测试学生", "hash");
        student.setStudentNo("S1001");
        student.setClassName("软件2501");
        return student;
    }

    private void whenNoUserExists(String username) {
        when(studentRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(teacherRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(adminRepository.findByUsername(username)).thenReturn(Optional.empty());
    }

    private void setCurrentUsername(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
