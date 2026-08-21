package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 交卷结果。
 *
 * @param examId        考试 ID
 * @param submittedAt   交卷时间
 * @param answeredCount 已作答题目数
 * @param totalCount    试卷题目总数
 */
public record ExamSubmitResult(
        String examId,
        LocalDateTime submittedAt,
        int answeredCount,
        int totalCount) {
}
