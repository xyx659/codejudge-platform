package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.AdminUserCreateRequest;
import com.codejudge.platform.dto.AdminUserSummary;
import com.codejudge.platform.dto.UserImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserImportServiceTest {

    @Mock
    private AdminUserService adminUserService;

    private UserImportService userImportService;

    @BeforeEach
    void setUp() {
        userImportService = new UserImportService(adminUserService);
    }

    @Test
    void 混合CSV应按行部分成功并返回Excel物理行号() {
        String csv = """
                role,username,name,password,studentNo,className
                STUDENT,,导入学生A,123456,S001,软件2501
                TEACHER,teacher-ok,导入教师A,teacher123,,
                STUDENT,,学生B,123456,S002,软件2501
                STUDENT,,学生C,123456,S002,软件2502
                ADMIN,admin-bad,非法管理员,admin123,,
                STUDENT,,缺少密码,,,
                TEACHER,teacher-extra-no,带学号教师,teacher123,T999,
                """;

        when(adminUserService.createUser(any(AdminUserCreateRequest.class)))
                .thenReturn(summary("S001", "STUDENT"))
                .thenReturn(summary("teacher-ok", "TEACHER"))
                .thenReturn(summary("S002", "STUDENT"))
                .thenThrow(new BadRequestException("账号已存在"));

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(7, result.total(), "总数据行数应等于 7");
        assertEquals(3, result.successCount(), "合法且不重复的数据应有 3 行成功");
        assertEquals(4, result.failedCount(), "非法数据应有 4 行失败");
        assertEquals(5, result.errors().get(0).row(), "重复账号应返回第 5 行");
        assertEquals("S002", result.errors().get(0).username(), "错误行应记录登录账号（学号）");
        assertEquals("账号已存在", result.errors().get(0).reason(), "重复账号原因应明确");
        assertEquals(6, result.errors().get(1).row(), "管理员角色错误应返回第 6 行");
        assertEquals(7, result.errors().get(2).row(), "空密码错误应返回第 7 行");
        assertEquals(8, result.errors().get(3).row(), "教师带学号错误应返回第 8 行");
    }

    @Test
    void 空密码行应该单独失败() {
        String csv = """
                role,username,name,password,studentNo,className
                STUDENT,,缺少密码,,S001,
                """;

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(0, result.successCount(), "空密码行不应该入库");
        assertEquals(1, result.failedCount(), "空密码应产生一条错误");
        assertEquals("密码不能为空", result.errors().get(0).reason(), "错误提示应说明密码必填");
        verifyNoInteractions(adminUserService);
    }

    @Test
    void 学生学号为空时应该单独失败() {
        String csv = """
                role,username,name,password,studentNo,className
                STUDENT,,缺少学号,123456,,
                """;

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(0, result.successCount(), "缺少学号的学生不应该入库");
        assertEquals("学号不能为空", result.errors().get(0).reason(), "错误提示应说明学号必填");
        verifyNoInteractions(adminUserService);
    }

    @Test
    void 教师填写学号时应该单独失败() {
        String csv = """
                role,username,name,password,studentNo,className
                TEACHER,teacher-no,教师带学号,teacher123,T999,
                """;

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(0, result.successCount(), "教师填写学号不应该入库");
        assertEquals("教师不能填写学号", result.errors().get(0).reason(), "错误提示应说明角色字段限制");
        verifyNoInteractions(adminUserService);
    }

    @Test
    void 管理员角色行应该单独失败() {
        String csv = """
                role,username,name,password,studentNo,className
                ADMIN,bad-admin,非法管理员,admin123,,
                """;

        UserImportResult result = userImportService.importUsers(csvFile(csv));

        assertEquals(0, result.successCount(), "管理员角色不应该通过 CSV 导入");
        assertEquals("仅支持导入学生或教师", result.errors().get(0).reason(),
                "错误提示应说明 CSV 可导入角色范围");
        verifyNoInteractions(adminUserService);
    }

    @Test
    void 支持带BOM的UTF8CSV文件() {
        String csv = """
                role,username,name,password,studentNo,className
                STUDENT,,BOM学生,123456,B001,软件2501
                """;
        when(adminUserService.createUser(any(AdminUserCreateRequest.class)))
                .thenReturn(summary("B001", "STUDENT"));

        UserImportResult result = userImportService.importUsers(csvFileWithBom(csv));

        assertEquals(1, result.total(), "BOM 文件应正常识别一条数据");
        assertEquals(1, result.successCount(), "BOM 文件中的合法数据应成功导入");
        assertEquals(0, result.failedCount(), "BOM 不应导致解析失败");
    }

    @Test
    void 超过5MB的文件应该拒绝() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        Arrays.fill(content, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile(
                "file", "too-large.csv", "text/csv", content);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userImportService.importUsers(file),
                "超过 5MB 的 CSV 文件应该被拒绝");

        assertEquals("CSV文件大小不能超过5MB", exception.getMessage(),
                "文件大小错误提示应准确");
        verifyNoInteractions(adminUserService);
    }

    @Test
    void 非CSV扩展名应该拒绝() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "users.txt", "text/plain", "not-csv".getBytes(StandardCharsets.UTF_8));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userImportService.importUsers(file),
                "非 CSV 文件应该被拒绝");

        assertEquals("仅支持上传CSV文件", exception.getMessage(), "文件类型错误提示应准确");
        verifyNoInteractions(adminUserService);
    }

    @Test
    void 表头不正确时应该整体拒绝() {
        String csv = """
                wrong,header
                STUDENT,abc
                """;

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userImportService.importUsers(csvFile(csv)),
                "CSV 表头错误时应该整体拒绝");

        assertEquals(
                "CSV表头不正确，应为：role,username,name,password,studentNo,className",
                exception.getMessage(),
                "表头错误提示应列出标准字段");
        verifyNoInteractions(adminUserService);
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile csvFileWithBom(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return new MockMultipartFile("file", "users.csv", "text/csv", withBom);
    }

    private AdminUserSummary summary(String username, String role) {
        return new AdminUserSummary(
                1L,
                username,
                "测试用户",
                role,
                "STUDENT".equals(role) ? "S001" : null,
                "STUDENT".equals(role) ? "软件2501" : null,
                LocalDateTime.now());
    }
}
