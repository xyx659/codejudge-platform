package com.codejudge.platform.dto;

/**
 * 教师端「学生成绩」列表项。
 *
 * <p>一行代表一名学生在一场考试中的成绩：是否交卷、卷面得分、试卷满分、是否及格，
 * 以及学生的姓名 / 账号 / 学号 / 班级，供列表展示与关键字、班级、及格筛选。</p>
 *
 * @param studentId     学生 ID（对应 users.id）
 * @param name          学生姓名
 * @param username      登录账号
 * @param studentNo     学号（可空）
 * @param className     班级（可空）
 * @param submitted     是否已交卷（至少作答一题）
 * @param achievedScore 卷面得分（每题取最佳得分并封顶为该题分值后累加）
 * @param fullScore     试卷满分（组卷各题分值之和）
 * @param passScore     及格分（可空）
 * @param passed        是否及格
 */
public record StudentScoreItem(
        Long studentId,
        String name,
        String username,
        String studentNo,
        String className,
        boolean submitted,
        int achievedScore,
        int fullScore,
        Integer passScore,
        boolean passed) {
}
