package com.codejudge.platform.dto;

/**
 * 组卷时提交的单条「题目 + 分值」。
 *
 * @param questionId 题目 ID（对应 MongoDB questions._id）
 * @param score      这道题在本场考试中的分值
 */
public record ExamQuestionItem(String questionId, Integer score) {
}
