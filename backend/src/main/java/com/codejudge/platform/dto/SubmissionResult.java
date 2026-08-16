package com.codejudge.platform.dto;

import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.entity.TestCaseResult;

import java.util.List;

/**
 * 提交结果（成绩 + AI 评审反馈）。
 *
 * <p>学生点开某次提交查看成绩时返回：最终得分、每个测试用例的通过情况（黑盒判题），
 * 以及 AI 评审报告（白盒分析，含通过率、代码质量分、反馈列表）。</p>
 *
 * @param submissionId  提交记录 ID（MySQL 的 submissions.id）
 * @param questionId    题目 ID
 * @param questionTitle 题目标题
 * @param judgeStatus   判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT
 * @param score         最终得分（评测未完成时为 null）
 * @param testResults   各测试用例的执行结果（黑盒判题）
 * @param aiReview      AI 评审报告（白盒分析）
 */
public record SubmissionResult(
        Long submissionId,
        String questionId,
        String questionTitle,
        String judgeStatus,
        Integer score,
        List<TestCaseResult> testResults,
        AiReview aiReview) {

    /**
     * 工厂方法：把 MongoDB 的提交明细 + 查到的题目标题，拼成结果 DTO。
     *
     * @param d             提交明细（来自 MongoDB，含测试结果和 AI 评审）
     * @param questionTitle 题目标题（来自 MongoDB 的 questions，已提前查好）
     * @param submissionId  提交记录 ID（来自 MySQL）
     */
    public static SubmissionResult from(SubmissionDetail d, String questionTitle, Long submissionId) {
        return new SubmissionResult(
                submissionId,
                d.getQuestionId(),
                questionTitle,
                d.getJudgeStatus(),
                d.getScore(),
                d.getTestResults(),
                d.getAiReview());
    }
}
