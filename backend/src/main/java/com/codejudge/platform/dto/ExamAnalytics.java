package com.codejudge.platform.dto;

import java.util.List;

/**
 * 一场考试的完整学情分析结果。
 *
 * @param stats               成绩统计指标
 * @param distribution        分数段分布（直方图）
 * @param abilities           每道题的掌握度分析
 * @param difficultyAbilities 按难度维度聚合的能力画像
 * @param knowledgeAbilities  按知识点（分类）维度聚合的能力画像
 */
public record ExamAnalytics(
        ScoreStats stats,
        List<ScoreBucket> distribution,
        List<AbilityItem> abilities,
        List<AbilityDimension> difficultyAbilities,
        List<AbilityDimension> knowledgeAbilities) {
}
