package com.codejudge.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交答案明细实体，对应 MongoDB 集合 {@code submission_details}。
 *
 * <p>一个提交对应一个文档，保存完整源码、每个测试用例的结果以及 AI 评审报告；
 * 通过 {@link #studentId} 关联 MySQL users，{@link #questionId} 关联本库 questions。</p>
 */
@Document(collection = "submission_details")
public class SubmissionDetail {

    /** 明细 ID（MongoDB 自动生成的字符串主键） */
    @Id
    private String id;

    /** 学生 ID（对应 users.id） */
    private Long studentId;

    /** 题目 ID（对应 questions._id） */
    private String questionId;

    /** 提交 ID（对应 MySQL 的 submissions.id，用来把两个库的记录关联起来） */
    private Long submissionId;

    /** 提交的完整源码 */
    private String sourceCode;

    /** 判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT */
    private String judgeStatus;

    /** 得分 */
    private Integer score;

    /** 各测试用例的执行结果 */
    private List<TestCaseResult> testResults = new ArrayList<TestCaseResult>();

    /** AI 评审报告（白盒分析） */
    private AiReview aiReview;

    /** 提交时间 */
    private LocalDateTime submittedAt = LocalDateTime.now();

    public String getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getJudgeStatus() {
        return judgeStatus;
    }

    public void setJudgeStatus(String judgeStatus) {
        this.judgeStatus = judgeStatus;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public List<TestCaseResult> getTestResults() {
        return testResults;
    }

    public void setTestResults(List<TestCaseResult> testResults) {
        this.testResults = testResults;
    }

    public AiReview getAiReview() {
        return aiReview;
    }

    public void setAiReview(AiReview aiReview) {
        this.aiReview = aiReview;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
