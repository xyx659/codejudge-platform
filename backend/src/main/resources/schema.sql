-- ============================================================
-- MySQL 建表脚本（由 Spring Boot 启动时通过 spring.sql.init 自动执行）
-- 对应 JPA 实体：Student、Teacher、Admin、Submission
--
-- 说明：
--   1. 三角色用户分表存储：students / teachers / admins。
--   2. MongoDB 的 questions / submission_details 无需建表，
--      首次插入时由 Spring Data MongoDB 自动创建集合。
--   3. 所有语句使用 IF NOT EXISTS，重复启动幂等，不会破坏已有数据。
-- ============================================================

CREATE TABLE IF NOT EXISTS students (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '学生 ID',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    password    VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 加密）',
    student_no  VARCHAR(20)  NULL COMMENT '学号，仅学生使用',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_students_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学生表';

CREATE TABLE IF NOT EXISTS teachers (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '教师 ID',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    password    VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 加密）',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_teachers_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '教师表';

CREATE TABLE IF NOT EXISTS admins (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员 ID',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    password    VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 加密）',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admins_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员表';

CREATE TABLE IF NOT EXISTS submissions (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '提交 ID',
    question_id  VARCHAR(50) NOT NULL COMMENT '题目 ID（对应 MongoDB questions._id）',
    student_id   BIGINT      NOT NULL COMMENT '学生 ID（对应 students.id）',
    judge_status VARCHAR(30) NULL COMMENT '判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT',
    score        INT         NULL COMMENT '最终得分',
    created_at   DATETIME    NOT NULL COMMENT '提交时间',
    PRIMARY KEY (id),
    KEY idx_submissions_student_id (student_id),
    KEY idx_submissions_question_id (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '提交记录元数据表';

CREATE TABLE IF NOT EXISTS system_configs (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置 ID',
    config_key   VARCHAR(64)  NOT NULL COMMENT '配置键',
    config_value TEXT         NOT NULL COMMENT '配置值',
    value_type   VARCHAR(20)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：INT / STRING / BOOLEAN / ENCRYPTED',
    encrypted    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否加密存储',
    description  VARCHAR(255) NULL COMMENT '配置说明',
    updated_by   VARCHAR(50)  NULL COMMENT '最后修改人',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version      BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_configs_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统动态配置表';

CREATE TABLE IF NOT EXISTS system_config_audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审计日志 ID',
    config_key  VARCHAR(64)  NOT NULL COMMENT '配置键',
    operation   VARCHAR(20)  NOT NULL COMMENT '操作类型：UPDATE',
    old_value   TEXT         NULL COMMENT '修改前值，敏感配置为空',
    new_value   TEXT         NULL COMMENT '修改后值，敏感配置为空',
    is_sensitive TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否为敏感配置',
    changed_by  VARCHAR(50)  NOT NULL COMMENT '操作管理员账号',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_config_audit_key_created (config_key, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统配置修改审计日志表';

CREATE TABLE IF NOT EXISTS operation_audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审计日志 ID',
    trace_id       VARCHAR(64)  NULL COMMENT '请求追踪 ID',
    user_id        BIGINT       NULL COMMENT '操作用户 ID',
    username       VARCHAR(50)  NULL COMMENT '操作账号',
    user_role      VARCHAR(20)  NULL COMMENT '操作角色',
    client_ip      VARCHAR(64)  NULL COMMENT '客户端 IP',
    http_method    VARCHAR(10)  NULL COMMENT 'HTTP 方法',
    request_uri    VARCHAR(255) NULL COMMENT '请求路径',
    module         VARCHAR(50)  NULL COMMENT '业务模块',
    operation      VARCHAR(50)  NULL COMMENT '操作类型',
    description    VARCHAR(255) NULL COMMENT '操作说明',
    request_params TEXT         NULL COMMENT '脱敏后的请求参数',
    success        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功',
    http_status    INT          NULL COMMENT 'HTTP 状态码',
    error_message  VARCHAR(255) NULL COMMENT '失败原因',
    duration_ms    BIGINT       NULL COMMENT '执行耗时',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_operation_audit_created (created_at),
    KEY idx_operation_audit_user (username, created_at),
    KEY idx_operation_audit_module (module, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务操作审计日志表';

INSERT IGNORE INTO system_configs
    (config_key, config_value, value_type, encrypted, description)
VALUES
    ('judge.timeout_ms', '3000', 'INT', 0, '单次评测超时时间（毫秒）'),
    ('judge.memory_mb', '256', 'INT', 0, '单次评测最大内存（MB）'),
    ('judge.max_concurrent', '10', 'INT', 0, '评测任务最大并发数'),
    ('ai.provider', 'DEEPSEEK', 'STRING', 0, '当前生效 AI 服务商'),
    ('ai.model', 'deepseek-chat', 'STRING', 0, '当前生效 AI 模型'),
    ('ai.base_url', 'https://api.deepseek.com', 'STRING', 0, 'AI API 基础地址'),
    ('ai.api_key', '', 'ENCRYPTED', 1, 'AI API Key，使用独立环境密钥加密'),
    ('limit.login.per_minute.global', '100', 'INT', 0, '登录接口全局每分钟限流'),
    ('limit.login.per_minute.per_user', '10', 'INT', 0, '登录接口单用户每分钟限流'),
    ('limit.login.per_minute.per_ip', '20', 'INT', 0, '登录接口单 IP 每分钟限流'),
    ('limit.ai.per_minute.global', '300', 'INT', 0, 'AI 调用全局每分钟限流'),
    ('limit.ai.per_minute.per_user', '30', 'INT', 0, 'AI 调用单用户每分钟限流'),
    ('limit.ai.per_minute.per_ip', '100', 'INT', 0, 'AI 调用单 IP 每分钟限流'),
    ('limit.submit.per_minute.global', '600', 'INT', 0, '代码提交全局每分钟限流'),
    ('limit.submit.per_minute.per_user', '60', 'INT', 0, '代码提交单用户每分钟限流'),
    ('limit.submit.per_minute.per_ip', '120', 'INT', 0, '代码提交单 IP 每分钟限流');
