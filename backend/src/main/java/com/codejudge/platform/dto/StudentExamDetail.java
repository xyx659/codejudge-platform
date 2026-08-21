package com.codejudge.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端考试详情（进试卷答题 / 交卷后回看用）。
 *
 * <p>与 {@link StudentExamSummary} 相比，多了一个展开后的题目列表
 * {@link #questions}，每道题带完整描述、测试用例，以及（已交卷时）学生的答案。</p>
 *
 * @param id            考试 ID
 * @param title         考试标题
 * @param description   考试说明
 * @param targetClass   目标班级
 * @param startTime     开始时间
 * @param endTime       结束时间
 * @param durationMinutes 时长（分钟）
 * @param status        时间窗状态：NOT_STARTED / ONGOING / ENDED
 * @param questionCount 题目数量
 * @param totalScore    总分
 * @param submitted     当前学生是否已交卷
 * @param questions     展开后的题目列表
 */
public record StudentExamDetail(
        String id,
        String title,
        String description,
        String targetClass,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,
        String status,
        int questionCount,
        int totalScore,
        boolean submitted,
        List<StudentExamQuestion> questions) {
}
