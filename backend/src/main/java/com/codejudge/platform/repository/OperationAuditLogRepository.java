package com.codejudge.platform.repository;

import com.codejudge.platform.entity.OperationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 业务操作审计日志数据访问接口。
 */
public interface OperationAuditLogRepository
        extends JpaRepository<OperationAuditLog, Long>,
        JpaSpecificationExecutor<OperationAuditLog> {
}
