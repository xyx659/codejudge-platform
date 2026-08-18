package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Exam;

import java.time.LocalDateTime;

/**
 * 考试列表项（摘要）。
 *
 * <p>列表不返回完整的组卷题目明细，只给出题目数量和总分，让列表更轻量；
 * 完整题目明细通过详情接口返回。</p>
 *
 * @param id            考试 ID
 * @param title         考试标题
 * @param status        状态：DRAFT / PUBLISHED / CLOSED
 * @param targetClass   目标班级
 * @param startTime     开始时间
 * @param endTime       结束时间
 * @param questionCount 题目数量
 * @param totalScore    试卷总分
 */
public record ExamSummary(
        String id,
        String title,
        String status,
        String targetClass,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int questionCount,
        int totalScore) {

    /** 工厂方法：把考试实体转成列表摘要（自动统计题目数与总分） */
    public static ExamSummary from(Exam e) {
        int totalScore = e.getQuestions().stream()
                .mapToInt(q -> q.getScore() == null ? 0 : q.getScore())
                .sum();
        return new ExamSummary(
                e.getId(),
                e.getTitle(),
                e.getStatus(),
                e.getTargetClass(),
                e.getStartTime(),
                e.getEndTime(),
                e.getQuestions().size(),
                totalScore);
    }
}
