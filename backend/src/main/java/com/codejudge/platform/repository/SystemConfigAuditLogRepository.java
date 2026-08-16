package com.codejudge.platform.repository;

import com.codejudge.platform.entity.SystemConfigAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 系统配置审计日志数据访问接口。
 */
public interface SystemConfigAuditLogRepository
        extends JpaRepository<SystemConfigAuditLog, Long> {

    List<SystemConfigAuditLog> findByConfigKeyOrderByCreatedAtDesc(String configKey);
}
