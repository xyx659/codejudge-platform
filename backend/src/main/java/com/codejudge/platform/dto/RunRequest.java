package com.codejudge.platform.dto;

import com.codejudge.platform.entity.QuestionTestCase;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 样例自测请求体（不落库、不触发 AI 评审）。
 *
 * @param questionId 题目 ID（对应 MongoDB 的 _id）
 * @param sourceCode 学生当前编辑器里的源码
 * @param language   判题语言（Java / C / C++ / Python / Go），为空时回退 Java
 * @param testCases  要运行的样例测试用例；为空时后端回退为题目全部测试用例
 */
public record RunRequest(
        @NotBlank(message = "题目 ID 不能为空") String questionId,
        @NotBlank(message = "代码不能为空") String sourceCode,
        String language,
        List<QuestionTestCase> testCases) {
}
