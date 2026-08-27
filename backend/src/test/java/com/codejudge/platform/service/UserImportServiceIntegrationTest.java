package com.codejudge.platform.service;

import com.codejudge.platform.dto.UserImportResult;
import com.codejudge.platform.entity.Teacher;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class UserImportServiceIntegrationTest {

    @Autowired
    private UserImportService userImportService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 真实数据库中CSV重复账号部分成功且第二行失败() {
        String username = uniqueUsername();
        String csv = """
                role,username,name,password,studentNo,className
                STUDENT,%s,重复学生,123456,S001,软件2501
                STUDENT,%s,重复学生2,123456,S002,软件2502
                """.formatted(username, username);

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(2, result.total(), "CSV 应有两条数据");
        assertEquals(1, result.successCount(), "第一行重复账号之前应成功入库");
        assertEquals(1, result.failedCount(), "第二行重复账号应失败");
        assertEquals(3, result.errors().get(0).row(), "重复行应返回 Excel 第 3 行");
        assertEquals("用户名已存在", result.errors().get(0).reason(), "重复账号原因应明确");
        assertTrue(studentRepository.findByUsername(username).isPresent(),
                "第一行用户应真实写入学生表");
    }

    @Test
    void 真实数据库中CSV用户名与已有教师重复时该行失败() {
        String username = uniqueUsername();
        teacherRepository.save(new Teacher(
                username,
                "已有教师",
                passwordEncoder.encode("teacher123")));

        String csv = """
                role,username,name,password,studentNo,className
                STUDENT,%s,跨表重复学生,123456,S001,软件2501
                """.formatted(username);

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(1, result.failedCount(), "跨教师表重复的行应失败");
        assertEquals("用户名已存在", result.errors().get(0).reason(),
                "跨表重复账号应返回用户名已存在");
        assertTrue(studentRepository.findByUsername(username).isEmpty(),
                "跨表重复账号不应写入学生表");
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String uniqueUsername() {
        return "csv-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
