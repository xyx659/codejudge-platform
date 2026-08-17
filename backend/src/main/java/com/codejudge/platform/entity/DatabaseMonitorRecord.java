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
 * 数据库监控历史快照实体，对应 MySQL 表 {@code database_monitor_records}。
 */
@Entity
@Table(name = "database_monitor_records")
public class DatabaseMonitorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime collectedAt;

    @Column(length = 20)
    private String mysqlStatus;

    @Column(length = 100)
    private String mysqlVersion;

    private Long mysqlUptimeSeconds;
    private Integer mysqlMaxConnections;
    private Integer mysqlCurrentConnections;
    private Double mysqlConnectionUsagePercent;
    private Double mysqlDatabaseSizeMb;
    private Long mysqlSlowQueries;
    private Long mysqlReplicationDelayMs;
    private Double mysqlDiskTotalMb;
    private Double mysqlDiskFreeMb;

    @Column(columnDefinition = "TEXT")
    private String mysqlTablesJson;

    @Column(columnDefinition = "TEXT")
    private String mysqlSlowQueriesJson;

    @Column(length = 255)
    private String mysqlErrorMessage;

    @Column(length = 20)
    private String mongoStatus;

    @Column(length = 100)
    private String mongoVersion;

    private Long mongoUptimeSeconds;
    private Integer mongoCurrentConnections;
    private Double mongoResidentMemoryMb;
    private Double mongoDatabaseSizeMb;
    private Double mongoDiskTotalMb;
    private Double mongoDiskFreeMb;

    @Column(columnDefinition = "TEXT")
    private String mongoOpcountersJson;

    @Column(columnDefinition = "TEXT")
    private String mongoCollectionsJson;

    @Column(length = 255)
    private String mongoErrorMessage;

    public DatabaseMonitorRecord() {
    }

    @PrePersist
    void prePersist() {
        collectedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public String getMysqlStatus() {
        return mysqlStatus;
    }

    public void setMysqlStatus(String mysqlStatus) {
        this.mysqlStatus = mysqlStatus;
    }

    public String getMysqlVersion() {
        return mysqlVersion;
    }

    public void setMysqlVersion(String mysqlVersion) {
        this.mysqlVersion = mysqlVersion;
    }

    public Long getMysqlUptimeSeconds() {
        return mysqlUptimeSeconds;
    }

    public void setMysqlUptimeSeconds(Long mysqlUptimeSeconds) {
        this.mysqlUptimeSeconds = mysqlUptimeSeconds;
    }

    public Integer getMysqlMaxConnections() {
        return mysqlMaxConnections;
    }

    public void setMysqlMaxConnections(Integer mysqlMaxConnections) {
        this.mysqlMaxConnections = mysqlMaxConnections;
    }

    public Integer getMysqlCurrentConnections() {
        return mysqlCurrentConnections;
    }

    public void setMysqlCurrentConnections(Integer mysqlCurrentConnections) {
        this.mysqlCurrentConnections = mysqlCurrentConnections;
    }

    public Double getMysqlConnectionUsagePercent() {
        return mysqlConnectionUsagePercent;
    }

    public void setMysqlConnectionUsagePercent(Double mysqlConnectionUsagePercent) {
        this.mysqlConnectionUsagePercent = mysqlConnectionUsagePercent;
    }

    public Double getMysqlDatabaseSizeMb() {
        return mysqlDatabaseSizeMb;
    }

    public void setMysqlDatabaseSizeMb(Double mysqlDatabaseSizeMb) {
        this.mysqlDatabaseSizeMb = mysqlDatabaseSizeMb;
    }

    public Long getMysqlSlowQueries() {
        return mysqlSlowQueries;
    }

    public void setMysqlSlowQueries(Long mysqlSlowQueries) {
        this.mysqlSlowQueries = mysqlSlowQueries;
    }

    public Long getMysqlReplicationDelayMs() {
        return mysqlReplicationDelayMs;
    }

    public void setMysqlReplicationDelayMs(Long mysqlReplicationDelayMs) {
        this.mysqlReplicationDelayMs = mysqlReplicationDelayMs;
    }

    public Double getMysqlDiskTotalMb() {
        return mysqlDiskTotalMb;
    }

    public void setMysqlDiskTotalMb(Double mysqlDiskTotalMb) {
        this.mysqlDiskTotalMb = mysqlDiskTotalMb;
    }

    public Double getMysqlDiskFreeMb() {
        return mysqlDiskFreeMb;
    }

    public void setMysqlDiskFreeMb(Double mysqlDiskFreeMb) {
        this.mysqlDiskFreeMb = mysqlDiskFreeMb;
    }

    public String getMysqlTablesJson() {
        return mysqlTablesJson;
    }

    public void setMysqlTablesJson(String mysqlTablesJson) {
        this.mysqlTablesJson = mysqlTablesJson;
    }

    public String getMysqlSlowQueriesJson() {
        return mysqlSlowQueriesJson;
    }

    public void setMysqlSlowQueriesJson(String mysqlSlowQueriesJson) {
        this.mysqlSlowQueriesJson = mysqlSlowQueriesJson;
    }

    public String getMysqlErrorMessage() {
        return mysqlErrorMessage;
    }

    public void setMysqlErrorMessage(String mysqlErrorMessage) {
        this.mysqlErrorMessage = mysqlErrorMessage;
    }

    public String getMongoStatus() {
        return mongoStatus;
    }

    public void setMongoStatus(String mongoStatus) {
        this.mongoStatus = mongoStatus;
    }

    public String getMongoVersion() {
        return mongoVersion;
    }

    public void setMongoVersion(String mongoVersion) {
        this.mongoVersion = mongoVersion;
    }

    public Long getMongoUptimeSeconds() {
        return mongoUptimeSeconds;
    }

    public void setMongoUptimeSeconds(Long mongoUptimeSeconds) {
        this.mongoUptimeSeconds = mongoUptimeSeconds;
    }

    public Integer getMongoCurrentConnections() {
        return mongoCurrentConnections;
    }

    public void setMongoCurrentConnections(Integer mongoCurrentConnections) {
        this.mongoCurrentConnections = mongoCurrentConnections;
    }

    public Double getMongoResidentMemoryMb() {
        return mongoResidentMemoryMb;
    }

    public void setMongoResidentMemoryMb(Double mongoResidentMemoryMb) {
        this.mongoResidentMemoryMb = mongoResidentMemoryMb;
    }

    public Double getMongoDatabaseSizeMb() {
        return mongoDatabaseSizeMb;
    }

    public void setMongoDatabaseSizeMb(Double mongoDatabaseSizeMb) {
        this.mongoDatabaseSizeMb = mongoDatabaseSizeMb;
    }

    public Double getMongoDiskTotalMb() {
        return mongoDiskTotalMb;
    }

    public void setMongoDiskTotalMb(Double mongoDiskTotalMb) {
        this.mongoDiskTotalMb = mongoDiskTotalMb;
    }

    public Double getMongoDiskFreeMb() {
        return mongoDiskFreeMb;
    }

    public void setMongoDiskFreeMb(Double mongoDiskFreeMb) {
        this.mongoDiskFreeMb = mongoDiskFreeMb;
    }

    public String getMongoOpcountersJson() {
        return mongoOpcountersJson;
    }

    public void setMongoOpcountersJson(String mongoOpcountersJson) {
        this.mongoOpcountersJson = mongoOpcountersJson;
    }

    public String getMongoCollectionsJson() {
        return mongoCollectionsJson;
    }

    public void setMongoCollectionsJson(String mongoCollectionsJson) {
        this.mongoCollectionsJson = mongoCollectionsJson;
    }

    public String getMongoErrorMessage() {
        return mongoErrorMessage;
    }

    public void setMongoErrorMessage(String mongoErrorMessage) {
        this.mongoErrorMessage = mongoErrorMessage;
    }
}
