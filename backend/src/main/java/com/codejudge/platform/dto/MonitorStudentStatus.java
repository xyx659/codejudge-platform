package com.codejudge.platform.dto;

/**
 * 监考时单个学生的作答状态。
 *
 * @param studentId       学生 ID
 * @param studentNo       学号
 * @param name            姓名
 * @param submittedCount  已作答（提交过）的题目数
 * @param totalQuestions  试卷题目总数
 * @param score           当前得分（已提交题目的最佳得分累加，按每题分值封顶）
 * @param status          状态文案：未开始 / 答题中 / 已交卷
 * @param switchTabCount  切屏次数（离开页面/失焦但不离开答题页）
 * @param leavePageCount  切页面次数（离开答题页/关闭页面）
 */
public record MonitorStudentStatus(
        Long studentId,
        String studentNo,
        String name,
        int submittedCount,
        int totalQuestions,
        int score,
        String status,
        int switchTabCount,
        int leavePageCount) {
}
