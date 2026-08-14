package com.codejudge.platform.entity;

/**
 * 单个测试用例的执行结果，内嵌于 {@link SubmissionDetail#testResults}。
 */
public class TestCaseResult {

    /** 用例名称 */
    private String testCaseName;

    /** 是否通过 */
    private boolean passed;

    /** 实际输出 */
    private String actual;

    /** 结果说明（如「通过」或错误信息） */
    private String message;

    /** 执行耗时（毫秒） */
    private long durationMs;

    public TestCaseResult() {
    }

    public TestCaseResult(String testCaseName, boolean passed, String actual, String message, long durationMs) {
        this.testCaseName = testCaseName;
        this.passed = passed;
        this.actual = actual;
        this.message = message;
        this.durationMs = durationMs;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public void setTestCaseName(String testCaseName) {
        this.testCaseName = testCaseName;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getActual() {
        return actual;
    }

    public void setActual(String actual) {
        this.actual = actual;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
