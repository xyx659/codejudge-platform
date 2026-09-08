package com.codejudge.platform.dto;

/**
 * 「我的成绩」里某场考试下的一道题的得分情况。
 *
 * @param submissionId  该题代表提交的 ID（用于点开查看 AI 评审与用例明细）
 * @param questionId    题目 ID
 * @param questionTitle 题目标题
 * @param judgeStatus   判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT
 * @param score         该题得分（评测未完成时为 null）
 */
public record StudentExamQuestionScore(
        Long submissionId,
        String questionId,
        String questionTitle,
        String judgeStatus,
        Integer score) {
}
