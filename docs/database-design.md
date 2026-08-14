# 数据库设计说明（MySQL + MongoDB）

## 概览

系统采用混合存储架构：

- **MySQL**：存储三角色用户信息、提交记录元数据（判卷摘要）
- **MongoDB**：存储题目相关信息、提交答案明细（含测试结果与 AI 评审报告）

两个数据库名称均为 `codejudge`，连接账号统一为 `test / 123456`。

## MySQL（关系型数据库）

库名：`codejudge`

### users（用户表）

存储学生、教师、管理员三类账号，通过 `role` 字段区分。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 用户 ID |
| `username` | VARCHAR(50) | 非空，唯一 | 登录账号 |
| `name` | VARCHAR(50) | 非空 | 姓名 |
| `password` | VARCHAR(100) | 非空 | 登录密码（后续改为加密存储） |
| `role` | VARCHAR(20) | 非空 | 角色：`ADMIN` / `TEACHER` / `STUDENT` |
| `student_no` | VARCHAR(20) | 可空 | 学号，仅学生使用，是学号的唯一权威来源 |
| `created_at` | DATETIME | 非空 | 创建时间 |

### submissions（提交记录元数据表）

只保存判卷摘要，用于成绩单、统计和分页查询；完整答案正文存放在 MongoDB。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | 主键，自增 | 提交 ID |
| `question_id` | VARCHAR(50) | 非空 | 题目 ID（对应 MongoDB `questions._id`） |
| `student_id` | BIGINT | 非空 | 学生 ID（对应 `users.id`） |
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

说明：`submission_details` 通过 `studentId` 关联 MySQL `users`，通过 `questionId` 关联本库 `questions`；学号只在 `users.student_no` 保存一份。

## 数据归属对照

| 业务数据 | 存储位置 | 说明 |
| --- | --- | --- |
| 学生/教师/管理员账号 | MySQL `users` | 通过 `role` 区分 |
| 学号 | MySQL `users.student_no` | 唯一权威来源 |
| 提交记录元数据 | MySQL `submissions` | 判卷摘要，便于统计 |
| 题目与测试用例 | MongoDB `questions` | 结构灵活，便于扩展 |
| 提交答案、测试结果、AI 评审 | MongoDB `submission_details` | 一个提交一个文档 |

## 后续扩展

- MySQL 补充：考试表、题库分类表、反作弊事件表、学情统计表
- MongoDB 补充：试卷快照、能力画像、教学诊断报告
- 使用 Flyway 管理 MySQL 表结构变更
