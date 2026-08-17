package com.codejudge.platform.dto;

/**
 * 分数段桶（用于成绩分布直方图）。
 *
 * @param label 分数段标签，如「90-100」
 * @param count 落在该分数段的学生人数
 */
public record ScoreBucket(String label, int count) {
}
