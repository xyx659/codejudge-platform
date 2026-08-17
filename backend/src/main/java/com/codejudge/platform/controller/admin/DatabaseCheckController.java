package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.DatabaseMonitorResponse;
import com.codejudge.platform.dto.DatabaseMonitorSnapshotResponse;
import com.codejudge.platform.repository.AdminRepository;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.TeacherRepository;
import com.codejudge.platform.service.DatabaseMonitorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库健康检查接口。
 *
 * <p>用于验证 MySQL 与 MongoDB 连接是否正常，并返回各表/集合初始化数据量。</p>
 */
@RestController
@RequestMapping("/api/admin/db")
public class DatabaseCheckController {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AdminRepository adminRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionDetailRepository submissionDetailRepository;
    private final DatabaseMonitorService databaseMonitorService;

    public DatabaseCheckController(StudentRepository studentRepository,
                                   TeacherRepository teacherRepository,
                                   AdminRepository adminRepository,
                                   QuestionRepository questionRepository,
                                   SubmissionRepository submissionRepository,
                                   SubmissionDetailRepository submissionDetailRepository,
                                   DatabaseMonitorService databaseMonitorService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.adminRepository = adminRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.databaseMonitorService = databaseMonitorService;
    }

    /** 检查两个数据库的连接状态与数据量 */
    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> check() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("mysql", Map.of(
                "status", "ok",
                "admins", adminRepository.count(),
                "teachers", teacherRepository.count(),
                "students", studentRepository.count(),
                "submissions", submissionRepository.count()
        ));
        data.put("mongodb", Map.of(
                "status", "ok",
                "questions", questionRepository.count(),
                "submission_details", submissionDetailRepository.count()
        ));
        return ApiResponse.ok(data);
    }

    /** 查询完整数据库监控状态 */
    @GetMapping("/status")
    public ApiResponse<DatabaseMonitorResponse> status() {
        return ApiResponse.ok(databaseMonitorService.getCurrentStatus());
    }

    /** 查询数据库监控历史快照 */
    @GetMapping("/history")
    public ApiResponse<PageResult<DatabaseMonitorSnapshotResponse>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        return ApiResponse.ok(databaseMonitorService.listHistory(
                page, size, startTime, endTime));
    }
}
