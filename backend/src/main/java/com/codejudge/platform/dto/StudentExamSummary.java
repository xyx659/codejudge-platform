package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 学生端「我的考试」列表项。
 *
 * <p>学生看到的是<b>试卷（考试）</b>，而不是题库题目。每项除了考试基本信息，
 * 还带一个按当前时间算出的状态 {@link #status}：</p>
 * <ul>
 *   <li>{@code NOT_STARTED} —— 未开始（还没到开始时间）</li>
 *   <li>{@code ONGOING} —— 进行中（在开始/结束时间之间）</li>
 *   <li>{@code ENDED} —— 已结束（已过结束时间）</li>
 * </ul>
 *
 * @param id            考试 ID（MongoDB exams._id）
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
 */
public record StudentExamSummary(
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
        boolean submitted) {
}
