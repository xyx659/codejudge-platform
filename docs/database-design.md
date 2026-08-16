# 数据库设计说明（MySQL + MongoDB）

## 概览

系统采用混合存储架构：

- **MySQL**：存储三角色用户信息、提交记录元数据（判卷摘要）
- **MongoDB**：存储题目相关信息、提交答案明细（含测试结果与 AI 评审报告）

两个数据库名称均为 `codejudge`，连接账号统一为 `test / 123456`。

## MySQL（关系型数据库）

库名：`codejudge`

### 用户表（按角色分表）

学生、教师、管理员三类账号分表存储，不再使用 `role` 字段区分（表名即角色）。

**students（学生表）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 学生 ID |
| `username` | VARCHAR(50) | 非空，唯一 | 登录账号 |
| `name` | VARCHAR(50) | 非空 | 姓名 |
| `password` | VARCHAR(100) | 非空 | 登录密码（BCrypt 加密存储） |
| `student_no` | VARCHAR(20) | 可空 | 学号，仅学生使用，是学号的唯一权威来源 |
| `created_at` | DATETIME | 非空 | 创建时间 |

**teachers（教师表）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 教师 ID |
| `username` | VARCHAR(50) | 非空，唯一 | 登录账号 |
| `name` | VARCHAR(50) | 非空 | 姓名 |
| `password` | VARCHAR(100) | 非空 | 登录密码（BCrypt 加密存储） |
| `created_at` | DATETIME | 非空 | 创建时间 |

**admins（管理员表）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 管理员 ID |
| `username` | VARCHAR(50) | 非空，唯一 | 登录账号 |
| `name` | VARCHAR(50) | 非空 | 姓名 |
| `password` | VARCHAR(100) | 非空 | 登录密码（BCrypt 加密存储） |
| `created_at` | DATETIME | 非空 | 创建时间 |

### submissions（提交记录元数据表）

只保存判卷摘要，用于成绩单、统计和分页查询；完整答案正文存放在 MongoDB。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 提交 ID |
| `question_id` | VARCHAR(50) | 非空 | 题目 ID（对应 MongoDB `questions._id`） |
| `student_id` | BIGINT | 非空 | 学生 ID（对应 `students.id`） |
| `judge_status` | VARCHAR(30) | 可空 | 判卷状态：`PENDING` / `RUN_COMPLETED` / `COMPILE_ERROR` / `TIMEOUT` |
| `score` | INT | 可空 | 最终得分 |
| `created_at` | DATETIME | 非空 | 提交时间 |

## MongoDB（非关系型数据库）

库名：`codejudge`

### questions（题目集合）

存储题目信息、难度、标签与测试用例，字段结构灵活，便于后续扩展题型。

```json
{
  "_id": "ObjectId",
  "title": "两数之和",
  "description": "实现 sum(int a, int b)，返回两数之和",
  "methodName": "sum",
  "language": "Java",
  "difficulty": "简单",
  "tags": ["数学", "基础"],
  "testCases": [
    { "name": "基本用例 1+2", "input": "1 2", "expected": "3" },
    { "name": "负数 -5+5", "input": "-5 5", "expected": "0" }
  ],
  "published": true,
  "createdAt": "2026-08-13T22:40:00"
}
```

### submission_details（提交答案明细集合）

存储学生提交的完整答案、每个测试用例结果以及 AI 评审报告，一个提交对应一个文档。

```json
{
  "_id": "ObjectId",
  "studentId": 3,
  "questionId": "ObjectId",
  "sourceCode": "public class Solution { ... }",
  "judgeStatus": "RUN_COMPLETED",
  "score": 91,
  "testResults": [
    {
      "testCaseName": "基本用例 1+2",
      "passed": true,
      "actual": "3",
      "message": "通过",
      "durationMs": 1
    }
  ],
  "aiReview": {
    "score": 91,
    "passRate": 100,
    "qualityScore": 70,
    "feedback": [
      "黑盒测试：通过 3/3 个用例。",
      "白盒分析：代码简洁，建议补充注释。"
    ]
  },
  "submittedAt": "2026-08-13T22:40:00"
}
```

说明：`submission_details` 通过 `studentId` 关联 MySQL `students`，通过 `questionId` 关联本库 `questions`；学号只在 `students.student_no` 保存一份。

## 数据归属对照

| 业务数据 | 存储位置 | 说明 |
| --- | --- | --- |
| 学生账号 | MySQL `students` | 分表存储 |
| 教师账号 | MySQL `teachers` | 分表存储 |
| 管理员账号 | MySQL `admins` | 分表存储 |
| 学号 | MySQL `students.student_no` | 唯一权威来源 |
| 提交记录元数据 | MySQL `submissions` | 判卷摘要，便于统计 |
| 题目与测试用例 | MongoDB `questions` | 结构灵活，便于扩展 |
| 提交答案、测试结果、AI 评审 | MongoDB `submission_details` | 一个提交一个文档 |

## 后续扩展

- MySQL 补充：考试表、题库分类表、反作弊事件表、学情统计表
- MongoDB 补充：试卷快照、能力画像、教学诊断报告
- 使用 Flyway 管理 MySQL 表结构变更

## 系统配置表

### system_configs（系统动态配置表）

采用键值模型保存评测、AI 和限流等运行时配置，便于后续新增配置项。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 配置 ID |
| `config_key` | VARCHAR(64) | 非空，唯一 | 配置键 |
| `config_value` | TEXT | 非空 | 配置值 |
| `value_type` | VARCHAR(20) | 非空 | `INT` / `STRING` / `BOOLEAN` / `ENCRYPTED` |
| `encrypted` | TINYINT(1) | 非空 | 是否加密存储 |
| `description` | VARCHAR(255) | 可空 | 配置说明 |
| `updated_by` | VARCHAR(50) | 可空 | 最后修改管理员 |
| `created_at` | DATETIME | 非空 | 创建时间 |
| `updated_at` | DATETIME | 非空 | 更新时间 |
| `version` | BIGINT | 非空 | 乐观锁版本号 |

默认配置包括：

```text
judge.timeout_ms
judge.memory_mb
judge.max_concurrent

ai.provider
ai.model
ai.base_url
ai.api_key

limit.login.per_minute.global
limit.login.per_minute.per_user
limit.login.per_minute.per_ip

limit.ai.per_minute.global
limit.ai.per_minute.per_user
limit.ai.per_minute.per_ip

limit.submit.per_minute.global
limit.submit.per_minute.per_user
limit.submit.per_minute.per_ip
```

`ai.api_key` 使用 `ENCRYPTED` 类型和加密标记，实际加密密钥通过环境变量提供，不写入数据库。

### system_config_audit_logs（配置审计日志表）

记录管理员对系统配置的修改操作。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 日志 ID |
| `config_key` | VARCHAR(64) | 非空 | 被修改的配置键 |
| `operation` | VARCHAR(20) | 非空 | 操作类型 |
| `old_value` | TEXT | 可空 | 修改前值，敏感配置不记录 |
| `new_value` | TEXT | 可空 | 修改后值，敏感配置不记录 |
| `is_sensitive` | TINYINT(1) | 非空 | 是否敏感配置 |
| `changed_by` | VARCHAR(50) | 非空 | 操作管理员账号 |
| `created_at` | DATETIME | 非空 | 操作时间 |

AI API Key 等敏感配置不会把明文旧值、新值写入审计表。

### operation_audit_logs（业务操作审计日志表）

记录登录和关键管理端写操作。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 日志 ID |
| `trace_id` | VARCHAR(64) | 可空 | 请求追踪 ID |
| `user_id` | BIGINT | 可空 | 操作用户 ID |
| `username` | VARCHAR(50) | 可空 | 操作账号 |
| `user_role` | VARCHAR(20) | 可空 | 操作角色 |
| `client_ip` | VARCHAR(64) | 可空 | 客户端 IP |
| `http_method` | VARCHAR(10) | 可空 | HTTP 方法 |
| `request_uri` | VARCHAR(255) | 可空 | 请求路径 |
| `module` | VARCHAR(50) | 可空 | 业务模块 |
| `operation` | VARCHAR(50) | 可空 | 操作类型 |
| `description` | VARCHAR(255) | 可空 | 操作说明 |
| `request_params` | TEXT | 可空 | 脱敏后的请求参数 |
| `success` | TINYINT(1) | 非空 | 是否成功 |
| `http_status` | INT | 可空 | HTTP 状态码 |
| `error_message` | VARCHAR(255) | 可空 | 失败原因 |
| `duration_ms` | BIGINT | 可空 | 执行耗时 |
| `created_at` | DATETIME | 非空 | 操作时间 |

密码、代码、AI Key、token 等敏感参数在写入前统一脱敏。
