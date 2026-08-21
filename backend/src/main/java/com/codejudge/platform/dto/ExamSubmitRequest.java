package com.codejudge.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 学生端「整卷交卷」请求体。
 *
 * <p>一场考试一次性交卷，前端把试卷内各题的答案一起提交上来；
 * {@link #answers} 里没出现、或 {@code sourceCode} 为空的题，后端会记为「未作答」。</p>
 *
 * @param answers 各题答案列表
 */
public record ExamSubmitRequest(@Valid @NotNull List<Answer> answers) {

    /**
     * 单题答案。
     *
     * @param questionId 题目 ID
     * @param sourceCode 学生写的源码（可为空串，表示未作答）
     */
    public record Answer(
            @NotBlank(message = "题目 ID 不能为空") String questionId,
            String sourceCode) {
    }
}
