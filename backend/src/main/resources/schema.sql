-- ============================================================
-- MySQL 建表脚本（由 Spring Boot 启动时通过 spring.sql.init 自动执行）
-- 对应 JPA 实体：User、Submission
--
-- 说明：
--   1. MongoDB 的 questions / submission_details 无需建表，
--      首次插入时由 Spring Data MongoDB 自动创建集合。
--   2. 所有语句使用 IF NOT EXISTS，重复启动幂等，不会破坏已有数据。
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    password    VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 加密）',
    role        VARCHAR(20)  NOT NULL COMMENT '角色：ADMIN / TEACHER / STUDENT',
    student_no  VARCHAR(20)  NULL COMMENT '学号，仅学生使用',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS submissions (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '提交 ID',
    question_id  VARCHAR(50) NOT NULL COMMENT '题目 ID（对应 MongoDB questions._id）',
    student_id   BIGINT      NOT NULL COMMENT '学生 ID（对应 users.id）',
    judge_status VARCHAR(30) NULL COMMENT '判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT',
    score        INT         NULL COMMENT '最终得分',
    created_at   DATETIME    NOT NULL COMMENT '提交时间',
    PRIMARY KEY (id),
    KEY idx_submissions_student_id (student_id),
    KEY idx_submissions_question_id (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '提交记录元数据表';
