package com.codejudge.platform.dto;

/**
 * 能力画像的某个聚合维度（按难度 / 按知识点）。
 *
 * <p>与 {@link AbilityItem}（逐题）不同，本类把同一维度（如难度档「简单/中等/困难」、
 * 知识点分类「算法/数据结构」）下的若干道题合并成一行，用于雷达图 / 条形图展示。</p>
 *
 * @param name           维度名（难度档或知识点分类名）
 * @param avgScoreRate   平均得分率（0~100，已作答学生按该维度满分归一化后的平均）
 * @param questionCount  该维度下包含的题目数
 * @param completionRate 完成率（0~100，作答该维度题目的学生人次占比）
 */
public record AbilityDimension(
        String name,
        double avgScoreRate,
        int questionCount,
        double completionRate) {
}
