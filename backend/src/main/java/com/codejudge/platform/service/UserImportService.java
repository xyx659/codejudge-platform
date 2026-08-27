package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.AdminUserCreateRequest;
import com.codejudge.platform.dto.UserImportError;
import com.codejudge.platform.dto.UserImportResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端用户 CSV 导入服务。
 *
 * <p>仅支持学生和教师，采用逐行处理：某行失败只记录错误，不影响其他行入库。</p>
 */
@Service
public class UserImportService {

    private static final Logger log = LoggerFactory.getLogger(UserImportService.class);
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> IMPORT_ROLES = Set.of("STUDENT", "TEACHER");
    private static final String[] HEADERS = {"role", "username", "name", "password", "studentNo", "className"};

    private final AdminUserService adminUserService;

    public UserImportService(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 导入 CSV 文件，返回成功数和逐行错误明细 */
    public UserImportResult importUsers(MultipartFile file) {
        validateFile(file);

        List<UserImportError> errors = new ArrayList<>();
        int total = 0;
        int successCount = 0;

        try (Reader reader = openReader(file);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader(HEADERS)
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            log.info("开始CSV导入：filename={}, size={}",
                    file.getOriginalFilename(), file.getSize());

            for (CSVRecord record : parser) {
                total++;
                // Commons CSV 将第一条数据记为 1，这里加回表头占用的第 1 行。
                int row = (int) record.getRecordNumber() + 1;

                try {
                    ImportRow importRow = parseRow(record, row);
                    adminUserService.createUser(new AdminUserCreateRequest(
                            importRow.role(),
                            importRow.username(),
                            importRow.name(),
                            importRow.password(),
                            importRow.studentNo(),
                            importRow.className()));
                    successCount++;
                } catch (BadRequestException e) {
                    String account = displayAccount(record);
                    errors.add(new UserImportError(row, account, e.getMessage()));
                    log.warn("CSV导入失败：row={}, account={}, reason={}",
                            row, account, e.getMessage());
                }
            }

            log.info("CSV导入完成：filename={}, total={}, successCount={}, failedCount={}",
                    file.getOriginalFilename(), total, successCount, errors.size());
            return new UserImportResult(
                    total, successCount, errors.size(), List.copyOf(errors));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("CSV表头不正确，应为：role,username,name,password,studentNo,className");
        } catch (IOException e) {
            log.error("CSV文件读取失败", e);
            throw new BadRequestException("CSV文件读取失败");
        }
    }

    /** 校验单行并转换为标准请求字段 */
    private ImportRow parseRow(CSVRecord record, int row) {
        String role = required(record, "role", "角色不能为空")
                .toUpperCase(Locale.ROOT);
        if (!IMPORT_ROLES.contains(role)) {
            throw new BadRequestException("仅支持导入学生或教师");
        }

        String name = required(record, "name", "姓名不能为空");
        String password = required(record, "password", "密码不能为空");
        String studentNo = value(record, "studentNo");
        String className = value(record, "className");

        // 学生用学号作为登录账号；教师用工号作为登录账号
        String username;
        if ("STUDENT".equals(role)) {
            username = required(record, "studentNo", "学号不能为空");
            if (username.length() > 20) {
                throw new BadRequestException("学号不能超过 20 个字符");
            }
        } else {
            username = required(record, "username", "工号不能为空");
            if (username.length() > 50) {
                throw new BadRequestException("工号不能超过 50 个字符");
            }
        }

        if (name.length() > 50) {
            throw new BadRequestException("姓名不能超过 50 个字符");
        }
        if (password.length() < 6 || password.length() > 100) {
            throw new BadRequestException("密码长度必须为 6 到 100 个字符");
        }
        if ("STUDENT".equals(role)) {
            if (className != null && className.length() > 50) {
                throw new BadRequestException("班级不能超过 50 个字符");
            }
        } else {
            if (studentNo != null) {
                throw new BadRequestException("教师不能填写学号");
            }
            if (className != null) {
                throw new BadRequestException("教师不能填写班级");
            }
        }

        return new ImportRow(role, username, name, password, studentNo, className);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择CSV文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("仅支持上传CSV文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("CSV文件大小不能超过5MB");
        }
    }

    /** 创建支持 UTF-8 BOM 的 Reader */
    private Reader openReader(MultipartFile file) throws IOException {
        PushbackReader reader = new PushbackReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != '\uFEFF') {
            reader.unread(first);
        }
        return reader;
    }

    private String value(CSVRecord record, String name) {
        String value = record.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 用于错误展示的账号：学生显示学号，教师显示工号 */
    private String displayAccount(CSVRecord record) {
        String role = value(record, "role");
        if ("STUDENT".equalsIgnoreCase(role)) {
            return value(record, "studentNo");
        }
        return value(record, "username");
    }

    private String required(CSVRecord record, String name, String message) {
        String value = value(record, name);
        if (value == null) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private record ImportRow(
            String role,
            String username,
            String name,
            String password,
            String studentNo,
            String className) {
    }
}
