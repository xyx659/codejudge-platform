package com.codejudge.platform.dto;

/**
 * 单道题的学情分析（知识点掌握度）。
 *
 * @param questionTitle  题目标题
 * @param avgScore       该题平均得分（已作答学生）
 * @param fullScore      该题满分
 * @param completionRate 完成率（0~100，作答该题的学生占比）
 */
public record AbilityItem(
        String questionTitle,
        double avgScore,
        int fullScore,
        double completionRate) {
}
