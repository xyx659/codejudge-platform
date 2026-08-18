package com.codejudge.platform.dto;

import java.util.List;
import java.util.Map;

/**
 * 教师工作台统计信息。
 *
 * @param questionCount          题目总数
 * @param publishedQuestionCount 已发布题目数
 * @param examCount              考试总数
 * @param publishedExamCount     已发布考试数
 * @param studentCount           学生总数
 * @param submissionCount        提交总数
 * @param categoryDistribution   各分类下的题目数（分类名 → 数量）
 * @param recentExams            最近创建的几场考试
 */
public record DashboardStats(
        long questionCount,
        long publishedQuestionCount,
        long examCount,
        long publishedExamCount,
        long studentCount,
        long submissionCount,
        Map<String, Long> categoryDistribution,
        List<ExamSummary> recentExams) {
}
