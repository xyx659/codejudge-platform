package com.codejudge.platform.dto;

import java.util.List;

/**
 * 「我的成绩」里的一场考试的成绩汇总（按考试分组）。
 *
 * @param examId        考试 ID（历史单题练习为空字符串）
 * @param examTitle     考试标题
 * @param achievedScore 学生得分（每题最佳得分之和，封顶为该题分值）
 * @param fullScore     卷面满分（组卷各题分值之和）
 * @param passScore     及格分（可空）
 * @param questions     每题得分明细
 */
public record StudentExamScore(
        String examId,
        String examTitle,
        int achievedScore,
        int fullScore,
        Integer passScore,
        List<StudentExamQuestionScore> questions) {
}
