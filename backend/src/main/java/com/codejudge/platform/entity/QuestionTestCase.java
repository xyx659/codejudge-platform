package com.codejudge.platform.entity;

/**
 * 题目测试用例，内嵌于 {@link Question#testCases}。
 *
 * <p>定义一次评测的输入与期望输出，用于黑盒判题比对。</p>
 */
public class QuestionTestCase {

    /** 用例名称（如「基本用例 1+2」） */
    private String name;

    /** 输入（按题目约定格式，如「1 2」） */
    private String input;

    /** 期望输出 */
    private String expected;

    public QuestionTestCase() {
    }

    public QuestionTestCase(String name, String input, String expected) {
        this.name = name;
        this.input = input;
        this.expected = expected;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }
}
