package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 代码提交请求体。
 *
 * @param questionId 题目 ID（对应 MongoDB 的 _id）
 * @param sourceCode 学生提交的完整源码
 */
public record SubmissionRequest(
        @NotBlank(message = "题目 ID 不能为空") String questionId,
        @NotBlank(message = "代码不能为空") String sourceCode) {
}
