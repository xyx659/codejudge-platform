package com.codejudge.platform.dto;

import com.codejudge.platform.entity.QuestionTestCase;
import jakarta.validation.constraints.NotBlank;

/**
 * 管理端新增或修改题目时提交的测试用例。
 */
public record QuestionTestCaseRequest(
        @NotBlank(message = "测试用例名称不能为空") String name,
        @NotBlank(message = "测试用例输入不能为空") String input,
        @NotBlank(message = "测试用例期望输出不能为空") String expected) {

    public QuestionTestCase toEntity() {
        return new QuestionTestCase(
                name == null ? null : name.trim(),
                input == null ? null : input.trim(),
                expected == null ? null : expected.trim());
    }
}
