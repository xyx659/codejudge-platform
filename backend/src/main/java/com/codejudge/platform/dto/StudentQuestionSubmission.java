package com.codejudge.platform.dto;

import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.entity.TestCaseResult;

import java.util.List;

/**
 * 学生某道题的提交视图（提交后回看用）。
 *
 * <p>相比 {@link SubmissionResult} 多返回 {@code sourceCode}，让学生在提交后
 * 仍能回看自己提交的代码；同时带上评测结果与 AI 评审，方便对照。</p>
 *
 * @param submissionId  提交记录 ID（MySQL 的 submissions.id）
 * @param questionId    题目 ID
 * @param questionTitle 题目标题
 * @param judgeStatus   判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT
 * @param score         得分（评测未完成时为 null）
 * @param sourceCode    提交的源码（回看「自己的回答」）
 * @param testResults   各测试用例的执行结果
 * @param aiReview      AI 评审报告
 */
public record StudentQuestionSubmission(
        Long submissionId,
        String questionId,
        String questionTitle,
        String judgeStatus,
        Integer score,
        String sourceCode,
        List<TestCaseResult> testResults,
        AiReview aiReview) {

    /**
     * 工厂方法：把 MongoDB 的提交明细 + 题目标题，拼成提交视图。
     *
     * @param d             提交明细（含源码、测试结果与 AI 评审）
     * @param questionTitle 题目标题（来自 MongoDB 的 questions）
     * @param submissionId  提交记录 ID（来自 MySQL）
     */
    public static StudentQuestionSubmission from(SubmissionDetail d, String questionTitle, Long submissionId) {
        return new StudentQuestionSubmission(
                submissionId,
                d.getQuestionId(),
                questionTitle,
                d.getJudgeStatus(),
                d.getScore(),
                d.getSourceCode(),
                d.getTestResults(),
                d.getAiReview());
    }
}
