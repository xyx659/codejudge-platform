package com.codejudge.platform.dto;

/**
 * 单个样例测试用例的自测结果。
 *
 * @param name       用例名称
 * @param passed     是否通过
 * @param actual     实际输出
 * @param expected   期望输出
 * @param message    结果说明（「通过」/ 超时 / 运行时错误 / 输出不符）
 * @param durationMs 执行耗时（毫秒）
 */
public record RunTestCaseResult(
        String name,
        boolean passed,
        String actual,
        String expected,
        String message,
        long durationMs) {
}
