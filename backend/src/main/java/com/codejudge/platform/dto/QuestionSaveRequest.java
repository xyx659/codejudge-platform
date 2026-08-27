package com.codejudge.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 管理端新增或修改题目的请求体。
 */
public record QuestionSaveRequest(
        @NotBlank(message = "题目标题不能为空") String title,
        String description,
        @NotBlank(message = "方法名不能为空") String methodName,
        String methodSignature,
        @NotBlank(message = "编程语言不能为空") String language,
        @NotBlank(message = "难度不能为空") String difficulty,
        List<String> tags,
        Boolean published,
        List<@Valid QuestionTestCaseRequest> testCases) {
}
