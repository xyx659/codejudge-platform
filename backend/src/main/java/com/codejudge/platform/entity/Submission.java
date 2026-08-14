package com.codejudge.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 提交记录元数据实体，对应 MySQL 表 {@code submissions}。
 *
 * <p>只保存判卷摘要，用于成绩单、统计与分页查询；
 * 完整的答案正文与评测明细存放在 MongoDB 的 submission_details。</p>
 */
@Entity
@Table(name = "submissions",
        indexes = {
                @Index(name = "idx_submissions_student_id", columnList = "student_id"),
                @Index(name = "idx_submissions_question_id", columnList = "question_id")
        })
public class Submission {

    /** 提交 ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 题目 ID（对应 MongoDB questions._id） */
    @Column(nullable = false, length = 50)
    private String questionId;

    /** 学生 ID（对应 users.id） */
    @Column(nullable = false)
    private Long studentId;

    /** 判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT */
    @Column(length = 30)
    private String judgeStatus;

    /** 最终得分 */
    private Integer score;

    /** 提交时间，创建后不可更新 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Submission() {
    }

    public Submission(String questionId, Long studentId) {
        this.questionId = questionId;
        this.studentId = studentId;
    }

    public Long getId() {
        return id;
    }

    public String getQuestionId() {
        return questionId;
    }

    public Long getStudentId() {
        return studentId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
