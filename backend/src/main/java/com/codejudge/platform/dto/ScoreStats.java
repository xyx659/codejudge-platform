package com.codejudge.platform.dto;

/**
 * 一场考试的成绩统计指标。
 *
 * @param totalStudents  学生总数
 * @param submittedCount 已提交（至少答过一题）的学生数
 * @param avgScore       平均分（已提交学生）
 * @param maxScore       最高分
 * @param minScore       最低分
 * @param passRate       及格率（0~100，按已提交学生中达到及格分者占比）
 * @param passScore      及格分
 */
public record ScoreStats(
        int totalStudents,
        int submittedCount,
        double avgScore,
        double maxScore,
        double minScore,
        double passRate,
        int passScore) {
}
