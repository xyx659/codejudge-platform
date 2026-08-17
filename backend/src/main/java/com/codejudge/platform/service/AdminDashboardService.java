package com.codejudge.platform.service;

import com.codejudge.platform.dto.AdminDashboardResponse;
import com.codejudge.platform.repository.AdminRepository;
import com.codejudge.platform.repository.OperationAuditLogRepository;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.SystemConfigRepository;
import com.codejudge.platform.repository.TeacherRepository;
import org.springframework.stereotype.Service;

/**
 * 管理端工作台统计服务。
 */
@Service
public class AdminDashboardService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AdminRepository adminRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;
    private final AuditLogService auditLogService;
    private final DatabaseMonitorService databaseMonitorService;

    public AdminDashboardService(
            StudentRepository studentRepository,
            TeacherRepository teacherRepository,
            AdminRepository adminRepository,
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository,
            SystemConfigRepository systemConfigRepository,
            OperationAuditLogRepository operationAuditLogRepository,
            AuditLogService auditLogService,
            DatabaseMonitorService databaseMonitorService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.adminRepository = adminRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.auditLogService = auditLogService;
        this.databaseMonitorService = databaseMonitorService;
    }

    public AdminDashboardResponse getDashboard() {
        var db = databaseMonitorService.getCurrentStatus();
        var recentAuditLogs = auditLogService.search(
                0, 5, null, null, null, null, null, null).list();
        return new AdminDashboardResponse(
                studentRepository.count(),
                teacherRepository.count(),
                adminRepository.count(),
                questionRepository.count(),
                questionRepository.countByPublishedTrue(),
                submissionRepository.count(),
                systemConfigRepository.count(),
                operationAuditLogRepository.count(),
                "ok".equals(db.mysql().status()),
                "ok".equals(db.mongo().status()),
                recentAuditLogs);
    }
}
