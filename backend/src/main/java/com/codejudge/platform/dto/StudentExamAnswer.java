package com.codejudge.platform.dto;

import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.TestCaseResult;

import java.util.List;

/**
 * 教师端查看某学生某道题的答卷视图。
 *
 * <p>相比列表项 {@link StudentScoreItem}，这里带出了该题满分、判卷状态、学生提交的
 * 源码，以及评测明细（每个测试用例的结果）与 AI 评审，供老师阅卷复查。</p>
 *
 * @param submissionId  该题代表提交的 ID（未提交时为 null）
 * @param questionId    题目 ID
 * @param questionTitle 题目标题（组卷快照）
 * @param fullScore     该题在本场考试中的满分
 * @param judgeStatus   判卷状态：RUN_COMPLETED / COMPILE_ERROR / TIMEOUT / UNANSWERED
 * @param score         该题得分（未出分为 null）
 * @param sourceCode    学生提交的源码（未提交时为 null）
 * @param testResults   各测试用例的执行结果
 * @param aiReview      AI 评审报告
 */
public record StudentExamAnswer(
        Long submissionId,
        String questionId,
        String questionTitle,
        int fullScore,
        String judgeStatus,
        Integer score,
        String sourceCode,
        List<TestCaseResult> testResults,
        AiReview aiReview) {
}
