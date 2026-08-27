package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.AdminUserChangeRoleRequest;
import com.codejudge.platform.dto.AdminUserCreateRequest;
import com.codejudge.platform.dto.AdminUserSummary;
import com.codejudge.platform.dto.AdminUserUpdateRequest;
import com.codejudge.platform.entity.Admin;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Teacher;
import com.codejudge.platform.entity.User;
import com.codejudge.platform.repository.AdminRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.TeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端用户管理业务逻辑。
 *
 * <p>三角色分别存储在 students / teachers / admins 三张表，
 * 管理端负责聚合查询、跨表账号唯一性校验和按角色增删改。</p>
 */
@Service
public class AdminUserService {

    private static final Set<String> ROLES = Set.of("ADMIN", "TEACHER", "STUDENT");
    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AdminRepository adminRepository;
    private final SubmissionRepository submissionRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(StudentRepository studentRepository,
                            TeacherRepository teacherRepository,
                            AdminRepository adminRepository,
                            SubmissionRepository submissionRepository,
                            PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.adminRepository = adminRepository;
        this.submissionRepository = submissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户列表，可筛选角色和搜索用户名/姓名。
     *
     * @param page    页码，从 0 开始
     * @param size    每页条数
     * @param role    角色筛选，可为空
     * @param keyword 用户名或姓名模糊搜索，可为空
     * @return 分页用户摘要
     */
    public PageResult<AdminUserSummary> listUsers(int page, int size, String role, String keyword) {
        String normalizedRole = normalizeOptionalRole(role);
        List<User> users = new ArrayList<>();

        if (normalizedRole == null || "STUDENT".equals(normalizedRole)) {
            users.addAll(studentRepository.findAll());
        }
        if (normalizedRole == null || "TEACHER".equals(normalizedRole)) {
            users.addAll(teacherRepository.findAll());
        }
        if (normalizedRole == null || "ADMIN".equals(normalizedRole)) {
            users.addAll(adminRepository.findAll());
        }

        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            users.removeIf(user -> !containsIgnoreCase(user.getUsername(), normalizedKeyword)
                    && !containsIgnoreCase(user.getName(), normalizedKeyword));
        }

        users.sort(Comparator.comparing(User::getCreatedAt).reversed());

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long startIndex = Math.min((long) safePage * safeSize, users.size());
        int from = (int) startIndex;
        int to = Math.min(from + safeSize, users.size());

        List<AdminUserSummary> content = users.subList(from, to).stream()
                .map(AdminUserSummary::from)
                .toList();
        log.debug("查询用户列表：page={}, size={}, role={}, keyword={}, total={}",
                safePage, safeSize, normalizedRole, normalizedKeyword, users.size());
        return new PageResult<>(content, safePage, safeSize, users.size());
    }

    /** 新增用户，密码入库前使用 BCrypt 加密 */
    public AdminUserSummary createUser(AdminUserCreateRequest request) {
        String role = normalizeRequiredRole(request.role());
        String name = requiredText(request.name(), "姓名不能为空");
        String studentNo = trimToNull(request.studentNo());
        String className = trimToNull(request.className());

        // 学生用学号作为登录账号；教师/管理员用工号作为登录账号
        String username;
        if ("STUDENT".equals(role)) {
            username = requiredText(studentNo, "学号不能为空");
        } else {
            username = requiredText(request.username(), "工号不能为空");
        }

        if (studentNo != null && !"STUDENT".equals(role)) {
            log.warn("新增用户被拒绝：角色={}，不允许填写学号", role);
            throw new BadRequestException("仅学生可以填写学号");
        }
        if (className != null && !"STUDENT".equals(role)) {
            log.warn("新增用户被拒绝：角色={}，不允许填写班级", role);
            throw new BadRequestException("仅学生可以填写班级");
        }
        if (usernameExists(username)) {
            log.warn("新增用户被拒绝：账号已存在，username={}", username);
            throw new BadRequestException("账号已存在");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User saved;
        switch (role) {
            case "STUDENT" -> {
                Student student = new Student(username, name, encodedPassword);
                student.setStudentNo(studentNo);
                student.setClassName(className);
                saved = studentRepository.save(student);
            }
            case "TEACHER" -> saved = teacherRepository.save(
                    new Teacher(username, name, encodedPassword));
            case "ADMIN" -> saved = adminRepository.save(
                    new Admin(username, name, encodedPassword));
            default -> throw new BadRequestException("角色不合法");
        }
        log.info("新增用户成功：role={}, username={}, id={}", role, username, saved.getId());
        return AdminUserSummary.from(saved);
    }

    /** 修改指定角色下的用户资料，密码为空时保持原密码 */
    public AdminUserSummary updateUser(String role, Long id, AdminUserUpdateRequest request) {
        String normalizedRole = normalizeRequiredRole(role);
        User user = findUser(normalizedRole, id);
        String name = requiredText(request.name(), "姓名不能为空");
        String studentNo = trimToNull(request.studentNo());
        String className = trimToNull(request.className());

        // 学生用学号作为登录账号；教师/管理员用工号作为登录账号
        String username;
        if ("STUDENT".equals(normalizedRole)) {
            username = requiredText(studentNo, "学号不能为空");
        } else {
            username = requiredText(request.username(), "工号不能为空");
        }

        if (studentNo != null && !"STUDENT".equals(normalizedRole)) {
            log.warn("修改用户被拒绝：role={}, id={}，不允许填写学号", normalizedRole, id);
            throw new BadRequestException("仅学生可以填写学号");
        }
        if (className != null && !"STUDENT".equals(normalizedRole)) {
            log.warn("修改用户被拒绝：role={}, id={}，不允许填写班级", normalizedRole, id);
            throw new BadRequestException("仅学生可以填写班级");
        }
        if (!username.equals(user.getUsername())
                && usernameExistsExcluding(username, normalizedRole, id)) {
            log.warn("修改用户被拒绝：账号已存在，role={}, id={}, username={}",
                    normalizedRole, id, username);
            throw new BadRequestException("账号已存在");
        }

        user.updateProfile(username, name);
        if (request.password() != null && !request.password().isBlank()) {
            user.updatePassword(passwordEncoder.encode(request.password().trim()));
        }
        if (user instanceof Student student) {
            student.setStudentNo(studentNo);
            student.setClassName(className);
        }

        saveUser(normalizedRole, user);
        log.info("修改用户成功：role={}, id={}, username={}",
                normalizedRole, id, user.getUsername());
        return AdminUserSummary.from(user);
    }

    /** 删除用户；学生已有提交记录时不允许删除 */
    public void deleteUser(String role, Long id) {
        String normalizedRole = normalizeRequiredRole(role);
        User user = findUser(normalizedRole, id);

        switch (normalizedRole) {
            case "STUDENT" -> {
                if (submissionRepository.existsByStudentId(id)) {
                    log.warn("删除用户被拒绝：学生已有提交记录，id={}, username={}",
                            id, user.getUsername());
                    throw new BadRequestException("该学生已有提交记录，不能删除");
                }
                studentRepository.delete((Student) user);
                log.info("删除学生成功：id={}, username={}", id, user.getUsername());
            }
            case "TEACHER" -> {
                teacherRepository.delete((Teacher) user);
                log.info("删除教师成功：id={}, username={}", id, user.getUsername());
            }
            case "ADMIN" -> {
                if (adminRepository.count() <= 1) {
                    log.warn("删除管理员被拒绝：系统仅剩一个管理员，id={}, username={}",
                            id, user.getUsername());
                    throw new BadRequestException("系统至少需要保留一个管理员");
                }
                adminRepository.delete((Admin) user);
                log.info("删除管理员成功：id={}, username={}", id, user.getUsername());
            }
            default -> throw new BadRequestException("角色不合法");
        }
    }

    /**
     * 用户角色迁移。
     *
     * <p>当前系统按角色分表，因此角色变更需要先在目标角色表创建新记录，
     * 再删除原角色表记录。整个过程放在一个事务中，任一步失败都会回滚。</p>
     */
    @Transactional
    public AdminUserSummary changeUserRole(String currentRole, Long id,
                                           AdminUserChangeRoleRequest request) {
        String normalizedCurrentRole = normalizeRequiredRole(currentRole);
        String targetRole = normalizeRequiredRole(request.targetRole());
        User user = findUser(normalizedCurrentRole, id);

        if (normalizedCurrentRole.equals(targetRole)) {
            throw new BadRequestException("目标角色不能与原角色相同");
        }
        if ("STUDENT".equals(normalizedCurrentRole)
                && submissionRepository.existsByStudentId(id)) {
            log.warn("角色迁移被拒绝：学生已有提交记录，id={}, username={}",
                    id, user.getUsername());
            throw new BadRequestException("该学生已有提交记录，不能修改角色");
        }
        if ("ADMIN".equals(normalizedCurrentRole) && adminRepository.count() <= 1) {
            log.warn("角色迁移被拒绝：系统仅剩一个管理员，id={}, username={}",
                    id, user.getUsername());
            throw new BadRequestException("系统至少需要保留一个管理员");
        }
        if ("ADMIN".equals(normalizedCurrentRole)
                && user.getUsername().equals(currentUsername())) {
            log.warn("角色迁移被拒绝：不能修改当前登录管理员的角色，id={}", id);
            throw new BadRequestException("不能修改当前登录管理员的角色");
        }
        if (usernameExistsInRole(targetRole, user.getUsername())) {
            log.warn("角色迁移被拒绝：目标角色已存在同名用户，username={}, targetRole={}",
                    user.getUsername(), targetRole);
            throw new BadRequestException("目标角色中已存在相同用户名");
        }

        log.info("开始角色迁移：fromRole={}, id={}, username={}, targetRole={}",
                normalizedCurrentRole, id, user.getUsername(), targetRole);

        User migrated = createUserForRole(user, targetRole);
        User saved = saveUserAndGet(targetRole, migrated);
        deleteUserForRole(normalizedCurrentRole, user);

        log.info("角色迁移成功：oldRole={}, oldId={}, newRole={}, newId={}, username={}",
                normalizedCurrentRole, id, targetRole, saved.getId(), saved.getUsername());
        return AdminUserSummary.from(saved);
    }

    /** 根据角色和 ID 查询用户，三个表的 ID 可以重复，因此必须带上角色 */
    private User findUser(String role, Long id) {
        return switch (role) {
            case "STUDENT" -> studentRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("用户不存在"));
            case "TEACHER" -> teacherRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("用户不存在"));
            case "ADMIN" -> adminRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("用户不存在"));
            default -> throw new BadRequestException("角色不合法");
        };
    }

    private void saveUser(String role, User user) {
        switch (role) {
            case "STUDENT" -> studentRepository.save((Student) user);
            case "TEACHER" -> teacherRepository.save((Teacher) user);
            case "ADMIN" -> adminRepository.save((Admin) user);
            default -> throw new BadRequestException("角色不合法");
        }
    }

    private User saveUserAndGet(String role, User user) {
        return switch (role) {
            case "STUDENT" -> studentRepository.save((Student) user);
            case "TEACHER" -> teacherRepository.save((Teacher) user);
            case "ADMIN" -> adminRepository.save((Admin) user);
            default -> throw new BadRequestException("角色不合法");
        };
    }

    private void deleteUserForRole(String role, User user) {
        switch (role) {
            case "STUDENT" -> studentRepository.delete((Student) user);
            case "TEACHER" -> teacherRepository.delete((Teacher) user);
            case "ADMIN" -> adminRepository.delete((Admin) user);
            default -> throw new BadRequestException("角色不合法");
        }
    }

    private User createUserForRole(User source, String targetRole) {
        User target = switch (targetRole) {
            case "STUDENT" -> {
                Student student = new Student(
                        source.getUsername(), source.getName(), source.getPassword());
                if (source instanceof Student oldStudent) {
                    student.setStudentNo(oldStudent.getStudentNo());
                    student.setClassName(oldStudent.getClassName());
                }
                yield student;
            }
            case "TEACHER" -> new Teacher(
                    source.getUsername(), source.getName(), source.getPassword());
            case "ADMIN" -> new Admin(
                    source.getUsername(), source.getName(), source.getPassword());
            default -> throw new BadRequestException("角色不合法");
        };
        target.copyCreatedAtFrom(source);
        return target;
    }

    private boolean usernameExists(String username) {
        return studentRepository.findByUsername(username).isPresent()
                || teacherRepository.findByUsername(username).isPresent()
                || adminRepository.findByUsername(username).isPresent();
    }

    private boolean usernameExistsExcluding(String username, String role, Long excludedId) {
        return switch (role) {
            case "STUDENT" -> studentRepository.findByUsername(username)
                    .filter(user -> !user.getId().equals(excludedId))
                    .isPresent()
                    || teacherRepository.findByUsername(username).isPresent()
                    || adminRepository.findByUsername(username).isPresent();
            case "TEACHER" -> teacherRepository.findByUsername(username)
                    .filter(user -> !user.getId().equals(excludedId))
                    .isPresent()
                    || studentRepository.findByUsername(username).isPresent()
                    || adminRepository.findByUsername(username).isPresent();
            case "ADMIN" -> adminRepository.findByUsername(username)
                    .filter(user -> !user.getId().equals(excludedId))
                    .isPresent()
                    || studentRepository.findByUsername(username).isPresent()
                    || teacherRepository.findByUsername(username).isPresent();
            default -> throw new BadRequestException("角色不合法");
        };
    }

    private boolean usernameExistsInRole(String role, String username) {
        return switch (role) {
            case "STUDENT" -> studentRepository.findByUsername(username).isPresent();
            case "TEACHER" -> teacherRepository.findByUsername(username).isPresent();
            case "ADMIN" -> adminRepository.findByUsername(username).isPresent();
            default -> throw new BadRequestException("角色不合法");
        };
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private String normalizeOptionalRole(String role) {
        String normalized = trimToNull(role);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) {
            throw new BadRequestException("角色不合法");
        }
        return normalized;
    }

    private String normalizeRequiredRole(String role) {
        String normalized = normalizeOptionalRole(role);
        if (normalized == null) {
            throw new BadRequestException("角色不能为空");
        }
        return normalized;
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
