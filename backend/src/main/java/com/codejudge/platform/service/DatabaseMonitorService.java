package com.codejudge.platform.service;

import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.DatabaseCollectionStat;
import com.codejudge.platform.dto.DatabaseMonitorResponse;
import com.codejudge.platform.dto.DatabaseMonitorSnapshotResponse;
import com.codejudge.platform.dto.DatabaseTableStat;
import com.codejudge.platform.dto.MongoStatusResponse;
import com.codejudge.platform.dto.MysqlStatusResponse;
import com.codejudge.platform.dto.SlowQueryDetail;
import com.codejudge.platform.entity.DatabaseMonitorRecord;
import com.codejudge.platform.repository.DatabaseMonitorRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库监控服务，负责当前状态采集和历史快照保存。
 */
@Service
public class DatabaseMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMonitorService.class);
    private static final long CACHE_MILLIS = 10_000;
    private static final List<String> MONITORED_TABLES = List.of(
            "students",
            "teachers",
            "admins",
            "submissions",
            "system_configs",
            "system_config_audit_logs",
            "operation_audit_logs");
    private static final List<String> MONITORED_COLLECTIONS = List.of(
            "questions",
            "submission_details");

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final DatabaseMonitorRecordRepository monitorRecordRepository;
    private final String mysqlDatabaseName;

    private volatile DatabaseMonitorResponse cachedResponse;
    private volatile long cachedAt;

    public DatabaseMonitorService(
            JdbcTemplate jdbcTemplate,
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            DatabaseMonitorRecordRepository monitorRecordRepository,
            @Value("${MYSQL_DATABASE:codejudge}") String mysqlDatabaseName) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.monitorRecordRepository = monitorRecordRepository;
        this.mysqlDatabaseName = mysqlDatabaseName;
    }

    /** 获取当前状态，10 秒内复用缓存 */
    public DatabaseMonitorResponse getCurrentStatus() {
        long now = System.currentTimeMillis();
        if (cachedResponse == null || now - cachedAt > CACHE_MILLIS) {
            cachedResponse = collectCurrent();
            cachedAt = now;
        }
        return cachedResponse;
    }

    /** 查询历史监控快照 */
    public PageResult<DatabaseMonitorSnapshotResponse> listHistory(
            int page,
            int size,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "collectedAt"));

        var records = startTime != null && endTime != null
                ? monitorRecordRepository
                        .findByCollectedAtBetweenOrderByCollectedAtDesc(
                                startTime, endTime, pageable)
                : monitorRecordRepository
                        .findAllByOrderByCollectedAtDesc(pageable);

        List<DatabaseMonitorSnapshotResponse> list = records.getContent().stream()
                .map(record -> new DatabaseMonitorSnapshotResponse(
                        record.getId(),
                        toResponse(record)))
                .toList();
        return new PageResult<>(
                list,
                safePage,
                safeSize,
                records.getTotalElements());
    }

    /** 每 5 分钟采集并保存历史快照 */
    @Scheduled(fixedDelay = 300_000)
    public void collectAndSave() {
        try {
            DatabaseMonitorResponse response = collectCurrent();
            cachedResponse = response;
            cachedAt = System.currentTimeMillis();
            monitorRecordRepository.save(toEntity(response));
        } catch (Exception e) {
            log.error("数据库监控历史快照保存失败", e);
        }
    }

    private DatabaseMonitorResponse collectCurrent() {
        return new DatabaseMonitorResponse(
                collectMysql(),
                collectMongo(),
                LocalDateTime.now());
    }

    private MysqlStatusResponse collectMysql() {
        try {
            String version = jdbcTemplate.queryForObject(
                    "SELECT VERSION()", String.class);
            long uptime = mysqlStatusLong("Uptime");
            int maxConnections = mysqlVariableInt("max_connections");
            int currentConnections = mysqlStatusInt("Threads_connected");
            long slowQueries = mysqlStatusLong("Slow_queries");
            double usage = maxConnections == 0
                    ? 0
                    : currentConnections * 100.0 / maxConnections;

            List<DatabaseTableStat> tables = mysqlTableStats();
            List<SlowQueryDetail> slowQueryDetails = mysqlSlowQueries();
            DiskInfo disk = mysqlDiskInfo();

            return new MysqlStatusResponse(
                    "ok",
                    version,
                    uptime,
                    maxConnections,
                    currentConnections,
                    usage,
                    mysqlDatabaseSize(),
                    slowQueries,
                    mysqlReplicationDelay(),
                    disk.totalMb(),
                    disk.freeMb(),
                    tables,
                    slowQueryDetails,
                    null);
        } catch (Exception e) {
            log.warn("MySQL 监控采集失败", e);
            return new MysqlStatusResponse(
                    "error",
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    e.getMessage());
        }
    }

    private MongoStatusResponse collectMongo() {
        try {
            Document serverStatus = mongoTemplate.executeCommand(
                    new Document("serverStatus", 1));
            Document connections = readDocument(serverStatus, "connections");
            Document memory = readDocument(serverStatus, "mem");
            Document opcounters = readDocument(serverStatus, "opcounters");

            Document dbStats = mongoTemplate.getDb().runCommand(
                    new Document("dbStats", 1));
            double databaseSizeMb = numberValue(dbStats, "dataSize", 0).doubleValue()
                    / 1024.0 / 1024.0;
            databaseSizeMb += numberValue(dbStats, "indexSize", 0).doubleValue()
                    / 1024.0 / 1024.0;

            DiskInfo disk = mongoDiskInfo();
            long uptime = numberValue(serverStatus, "uptime", 0).longValue();
            if (uptime < 0) {
                uptime = -uptime;
            }
            return new MongoStatusResponse(
                    "ok",
                    serverStatus.getString("version"),
                    uptime,
                    connections == null ? 0 : connections.getInteger("current", 0),
                    memory == null ? 0 : memory.getInteger("resident", 0) / 1024.0,
                    databaseSizeMb,
                    disk.totalMb(),
                    disk.freeMb(),
                    mongoCollectionStats(),
                    mongoOpcounters(opcounters),
                    null);
        } catch (Exception e) {
            log.warn("MongoDB 监控采集失败", e);
            return new MongoStatusResponse(
                    "error",
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    Map.of(),
                    e.getMessage());
        }
    }

    private List<DatabaseTableStat> mysqlTableStats() {
        return jdbcTemplate.query(
                """
                SELECT table_name, table_rows,
                       ROUND(data_length / 1024 / 1024, 2),
                       ROUND(index_length / 1024 / 1024, 2)
                FROM information_schema.tables
                WHERE table_schema = ?
                ORDER BY data_length + index_length DESC
                """,
                (rs, rowNum) -> new DatabaseTableStat(
                        rs.getString("table_name"),
                        rs.getLong("table_rows"),
                        rs.getDouble(3),
                        rs.getDouble(4)),
                mysqlDatabaseName).stream()
                .filter(item -> MONITORED_TABLES.contains(item.tableName()))
                .toList();
    }

    private List<SlowQueryDetail> mysqlSlowQueries() {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT AVG_TIMER_WAIT / 1000000000000 AS duration_seconds,
                           SUM_LOCK_TIME / 1000000000000 AS lock_seconds,
                           SUM_ROWS_EXAMINED AS rows_examined,
                           SUM_ROWS_SENT AS rows_sent,
                           LEFT(DIGEST_TEXT, 2000) AS sql_text
                    FROM performance_schema.events_statements_summary_by_digest
                    WHERE DIGEST_TEXT IS NOT NULL
                    ORDER BY AVG_TIMER_WAIT DESC
                    LIMIT 50
                    """,
                    (rs, rowNum) -> new SlowQueryDetail(
                            rs.getDouble("duration_seconds"),
                            rs.getDouble("lock_seconds"),
                            rs.getLong("rows_examined"),
                            rs.getLong("rows_sent"),
                            rs.getString("sql_text")));
        } catch (Exception e) {
            log.debug("无法读取 MySQL performance_schema 慢查询详情", e);
            return List.of();
        }
    }

    private List<DatabaseCollectionStat> mongoCollectionStats() {
        List<DatabaseCollectionStat> result = new ArrayList<>();
        for (String collectionName : MONITORED_COLLECTIONS) {
            try {
                Document stats = mongoTemplate.getDb().runCommand(
                        new Document("collStats", collectionName));
                result.add(new DatabaseCollectionStat(
                        collectionName,
                        numberValue(stats, "count", 0).longValue(),
                        numberValue(stats, "storageSize", 0).doubleValue()
                                / 1024.0 / 1024.0));
            } catch (Exception e) {
                log.warn("MongoDB 集合监控失败：collection={}", collectionName, e);
            }
        }
        return result;
    }

    private Map<String, Long> mongoOpcounters(Document opcounters) {
        if (opcounters == null) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of(
                "insert", "query", "update", "delete", "getmore", "command")) {
            result.put(key, numberValue(opcounters, key, 0).longValue());
        }
        return result;
    }

    private double mysqlDatabaseSize() {
        Double size = jdbcTemplate.queryForObject(
                """
                SELECT ROUND(
                    SUM(data_length + index_length) / 1024 / 1024,
                    2)
                FROM information_schema.tables
                WHERE table_schema = ?
                """,
                Double.class,
                mysqlDatabaseName);
        return size == null ? 0 : size;
    }

    private Long mysqlReplicationDelay() {
        try {
            return jdbcTemplate.query("SHOW REPLICA STATUS", rs -> {
                if (rs.next()) {
                    Object delay = rs.getObject("Seconds_Behind_Source");
                    return delay == null ? null : rs.getLong("Seconds_Behind_Source");
                }
                return null;
            });
        } catch (Exception e) {
            log.debug("当前 MySQL 未配置主从复制", e);
            return null;
        }
    }

    private String mysqlStatusValue(String name) {
        return jdbcTemplate.queryForObject(
                """
                SELECT VARIABLE_VALUE
                FROM performance_schema.global_status
                WHERE VARIABLE_NAME = ?
                """,
                String.class,
                name);
    }

    private String mysqlVariableValue(String name) {
        return jdbcTemplate.queryForObject(
                """
                SELECT VARIABLE_VALUE
                FROM performance_schema.global_variables
                WHERE VARIABLE_NAME = ?
                """,
                String.class,
                name);
    }

    private long mysqlStatusLong(String name) {
        try {
            long value = Long.parseUnsignedLong(mysqlStatusValue(name));
            if (value < 0) {
                value = -value;
            }
            return value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int mysqlStatusInt(String name) {
        try {
            return Integer.parseInt(mysqlStatusValue(name));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int mysqlVariableInt(String name) {
        try {
            return Integer.parseInt(mysqlVariableValue(name));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private DiskInfo mysqlDiskInfo() {
        String datadir = jdbcTemplate.queryForObject(
                """
                SELECT VARIABLE_VALUE
                FROM performance_schema.global_variables
                WHERE VARIABLE_NAME = 'datadir'
                """,
                String.class);
        return diskInfo(new File(datadir == null ? "/var/lib/mysql" : datadir));
    }

    private DiskInfo mongoDiskInfo() {
        try {
            Document options = mongoTemplate.executeCommand(
                    new Document("getCmdLineOpts", 1));
            Document parsed = readDocument(options, "parsed");
            Document storage = readDocument(parsed, "storage");
            String dbPath = storage == null ? null : storage.getString("dbPath");
            return diskInfo(new File(dbPath == null ? "/var/lib/mongodb" : dbPath));
        } catch (Exception e) {
            log.debug("无法读取 MongoDB 数据目录", e);
            return diskInfo(new File("/var/lib/mongodb"));
        }
    }

    private DiskInfo diskInfo(File directory) {
        File root = directory.exists() ? directory : directory.getParentFile();
        if (root == null || !root.exists()) {
            return new DiskInfo(0, 0);
        }
        return new DiskInfo(
                root.getTotalSpace() / 1024.0 / 1024.0,
                root.getUsableSpace() / 1024.0 / 1024.0);
    }

    private DatabaseMonitorRecord toEntity(DatabaseMonitorResponse response) {
        DatabaseMonitorRecord record = new DatabaseMonitorRecord();
        record.setMysqlStatus(response.mysql().status());
        record.setMysqlVersion(response.mysql().version());
        record.setMysqlUptimeSeconds(response.mysql().uptimeSeconds());
        record.setMysqlMaxConnections(response.mysql().maxConnections());
        record.setMysqlCurrentConnections(response.mysql().currentConnections());
        record.setMysqlConnectionUsagePercent(response.mysql().connectionUsagePercent());
        record.setMysqlDatabaseSizeMb(response.mysql().databaseSizeMb());
        record.setMysqlSlowQueries(response.mysql().slowQueries());
        record.setMysqlReplicationDelayMs(response.mysql().replicationDelayMs());
        record.setMysqlDiskTotalMb(response.mysql().diskTotalMb());
        record.setMysqlDiskFreeMb(response.mysql().diskFreeMb());
        record.setMysqlTablesJson(toJson(response.mysql().tables()));
        record.setMysqlSlowQueriesJson(toJson(response.mysql().slowQueryDetails()));
        record.setMysqlErrorMessage(response.mysql().errorMessage());

        record.setMongoStatus(response.mongo().status());
        record.setMongoVersion(response.mongo().version());
        record.setMongoUptimeSeconds(response.mongo().uptimeSeconds());
        record.setMongoCurrentConnections(response.mongo().currentConnections());
        record.setMongoResidentMemoryMb(response.mongo().residentMemoryMb());
        record.setMongoDatabaseSizeMb(response.mongo().databaseSizeMb());
        record.setMongoDiskTotalMb(response.mongo().diskTotalMb());
        record.setMongoDiskFreeMb(response.mongo().diskFreeMb());
        record.setMongoOpcountersJson(toJson(response.mongo().opcounters()));
        record.setMongoCollectionsJson(toJson(response.mongo().collections()));
        record.setMongoErrorMessage(response.mongo().errorMessage());
        return record;
    }

    private DatabaseMonitorResponse toResponse(DatabaseMonitorRecord record) {
        return new DatabaseMonitorResponse(
                new MysqlStatusResponse(
                        record.getMysqlStatus(),
                        record.getMysqlVersion(),
                        value(record.getMysqlUptimeSeconds()),
                        value(record.getMysqlMaxConnections()),
                        value(record.getMysqlCurrentConnections()),
                        value(record.getMysqlConnectionUsagePercent()),
                        value(record.getMysqlDatabaseSizeMb()),
                        value(record.getMysqlSlowQueries()),
                        record.getMysqlReplicationDelayMs(),
                        value(record.getMysqlDiskTotalMb()),
                        value(record.getMysqlDiskFreeMb()),
                        fromJson(record.getMysqlTablesJson(),
                                new TypeReference<List<DatabaseTableStat>>() {}),
                        fromJson(record.getMysqlSlowQueriesJson(),
                                new TypeReference<List<SlowQueryDetail>>() {}),
                        record.getMysqlErrorMessage()),
                new MongoStatusResponse(
                        record.getMongoStatus(),
                        record.getMongoVersion(),
                        value(record.getMongoUptimeSeconds()),
                        value(record.getMongoCurrentConnections()),
                        value(record.getMongoResidentMemoryMb()),
                        value(record.getMongoDatabaseSizeMb()),
                        value(record.getMongoDiskTotalMb()),
                        value(record.getMongoDiskFreeMb()),
                        fromJson(record.getMongoCollectionsJson(),
                                new TypeReference<List<DatabaseCollectionStat>>() {}),
                        fromJson(record.getMongoOpcountersJson(),
                                new TypeReference<Map<String, Long>>() {}),
                        record.getMongoErrorMessage()),
                record.getCollectedAt());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private Document readDocument(Document parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        return value instanceof Document document ? document : null;
    }

    private Number numberValue(Document document, String key, Number fallback) {
        Object value = document == null ? null : document.get(key);
        return value instanceof Number number ? number : fallback;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double value(Double value) {
        return value == null ? 0 : value;
    }

    private record DiskInfo(double totalMb, double freeMb) {
    }
}
