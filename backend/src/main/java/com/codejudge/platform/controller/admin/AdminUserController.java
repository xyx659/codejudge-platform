package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.AuditOperation;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.AdminUserChangeRoleRequest;
import com.codejudge.platform.dto.AdminUserCreateRequest;
import com.codejudge.platform.dto.AdminUserSummary;
import com.codejudge.platform.dto.AdminUserUpdateRequest;
import com.codejudge.platform.dto.UserImportResult;
import com.codejudge.platform.service.AdminUserService;
import com.codejudge.platform.service.UserImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 管理端用户管理接口。
 *
 * <p>因为学生、教师和管理员分别存表且主键可能重复，
 * 修改、删除接口均使用 {@code role + id} 定位用户。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserImportService userImportService;

    public AdminUserController(AdminUserService adminUserService,
                               UserImportService userImportService) {
        this.adminUserService = adminUserService;
        this.userImportService = userImportService;
    }

    /** 用户列表，可按角色和用户名/姓名筛选 */
    @GetMapping
    public ApiResponse<PageResult<AdminUserSummary>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminUserService.listUsers(page, size, role, keyword));
    }

    /** 新增用户 */
    @PostMapping
    @AuditOperation(
            module = "用户管理",
            operation = "CREATE_USER",
            description = "新增用户")
    public ApiResponse<AdminUserSummary> create(
            @Valid @RequestBody AdminUserCreateRequest request) {
        return ApiResponse.ok(adminUserService.createUser(request));
    }

    /** 批量导入学生和教师 CSV */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditOperation(
            module = "用户管理",
            operation = "IMPORT_USERS",
            description = "批量导入用户")
    public ApiResponse<UserImportResult> importUsers(
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userImportService.importUsers(file));
    }

    /** 下载 CSV 导入模板 */
    @GetMapping(value = "/import-template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> importTemplate() {
        String content = "\uFEFFrole,username,name,password,studentNo\n"
                + "STUDENT,s2026001,张三,123456,2026001\n"
                + "TEACHER,t1001,王老师,teacher123,\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"user-import-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    /** 修改指定角色下的用户资料 */
    @PutMapping("/{role}/{id}")
    @AuditOperation(
            module = "用户管理",
            operation = "UPDATE_USER",
            description = "修改用户")
    public ApiResponse<AdminUserSummary> update(
            @PathVariable String role,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        return ApiResponse.ok(adminUserService.updateUser(role, id, request));
    }

    /** 修改指定用户的角色 */
    @PutMapping("/{role}/{id}/role")
    @AuditOperation(
            module = "用户管理",
            operation = "CHANGE_USER_ROLE",
            description = "修改用户角色")
    public ApiResponse<AdminUserSummary> changeRole(
            @PathVariable String role,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserChangeRoleRequest request) {
        return ApiResponse.ok(adminUserService.changeUserRole(role, id, request));
    }

    /** 删除指定角色下的用户 */
    @DeleteMapping("/{role}/{id}")
    @AuditOperation(
            module = "用户管理",
            operation = "DELETE_USER",
            description = "删除用户")
    public ApiResponse<Void> delete(@PathVariable String role, @PathVariable Long id) {
        adminUserService.deleteUser(role, id);
        return ApiResponse.ok(null);
    }
}
