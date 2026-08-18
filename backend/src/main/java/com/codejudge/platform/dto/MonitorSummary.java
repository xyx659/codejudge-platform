package com.codejudge.platform.dto;

import java.util.List;

/**
 * 监考总览：一场考试的整体进度 + 每个学生的作答状态 + 预警列表。
 *
 * <p>前端监考页用 5 秒轮询拉取本对象，一次拿到全部数据。</p>
 *
 * @param examId         考试 ID
 * @param examTitle      考试标题
 * @param status         考试状态
 * @param totalStudents  学生总数（系统全部学生）
 * @param submittedCount 已提交（至少答过一题）的学生数
 * @param avgScore       已提交学生的平均分
 * @param students       每个学生的作答状态
 * @param alerts         预警列表
 */
public record MonitorSummary(
        String examId,
        String examTitle,
        String status,
        int totalStudents,
        int submittedCount,
        double avgScore,
        List<MonitorStudentStatus> students,
        List<AlertItem> alerts) {
}
