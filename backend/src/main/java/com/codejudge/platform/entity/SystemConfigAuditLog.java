package com.codejudge.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 系统配置修改审计日志，对应 MySQL 表 {@code system_config_audit_logs}。
 */
@Entity
@Table(name = "system_config_audit_logs")
public class SystemConfigAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String configKey;

    @Column(nullable = false, length = 20)
    private String operation;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

    @Column(nullable = false, length = 50)
    private String changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SystemConfigAuditLog() {
    }

    public SystemConfigAuditLog(String configKey,
                                String operation,
                                String oldValue,
                                String newValue,
                                boolean sensitive,
                                String changedBy) {
        this.configKey = configKey;
        this.operation = operation;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.sensitive = sensitive;
        this.changedBy = changedBy;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
