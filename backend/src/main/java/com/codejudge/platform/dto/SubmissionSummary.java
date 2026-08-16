package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Submission;

import java.time.LocalDateTime;

/**
 * 提交记录列表项（摘要）。
 *
 * <p>注意：提交记录本身存在 MySQL，但「题目标题」在 MongoDB。
 * 所以这个类里的 {@code questionTitle} 不是从 {@link Submission} 直接来的，
 * 而是由 Service 额外查 MongoDB 后拼进来的。</p>
 *
 * @param id            提交记录 ID（MySQL 的 submissions.id）
 * @param questionId    题目 ID（对应 MongoDB 的 _id）
 * @param questionTitle 题目标题（从 MongoDB 补进来的，方便前端展示）
 * @param judgeStatus   判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT
 * @param score         得分（还没评测完时为 null）
 * @param createdAt     提交时间
 */
public record SubmissionSummary(
        Long id,
        String questionId,
        String questionTitle,
        String judgeStatus,
        Integer score,
        LocalDateTime createdAt) {

    /**
     * 工厂方法：把 MySQL 的提交记录 + 查到的题目标题，拼成列表项。
     *
     * @param s             提交记录（来自 MySQL）
     * @param questionTitle 题目标题（来自 MongoDB，已提前批量查好）
     */
    public static SubmissionSummary from(Submission s, String questionTitle) {
        return new SubmissionSummary(
                s.getId(),
                s.getQuestionId(),
                questionTitle,
                s.getJudgeStatus(),
                s.getScore(),
                s.getCreatedAt());
    }
}
