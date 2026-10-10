package com.codejudge.platform.dto;

import java.util.List;

/**
 * 样例自测结果。
 *
 * @param compileError 编译错误信息；编译通过时为 {@code null}
 * @param results      各用例运行结果；编译失败时为空列表
 */
public record RunResult(
        String compileError,
        List<RunTestCaseResult> results) {
}
