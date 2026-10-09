package com.codejudge.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JudgeOutputMatcher} 输出比对单测：覆盖精确、标量浮点、数组有序/无序、
 * 数组内数值近似比对（多语言浮点序列化差异）。
 */
class JudgeOutputMatcherTest {

    @Test
    void 精确相等直接匹配() {
        assertTrue(JudgeOutputMatcher.outputMatches("[1,2,3]", "[1,2,3]"));
        assertTrue(JudgeOutputMatcher.outputMatches("\"abc\"", "\"abc\""));
    }

    @Test
    void 单个数值允许浮点误差() {
        assertTrue(JudgeOutputMatcher.outputMatches("3", "3.000001"));
        assertTrue(JudgeOutputMatcher.outputMatches("3.14", "3.1400001"));
        assertFalse(JudgeOutputMatcher.outputMatches("3", "3.001"));
    }

    @Test
    void 数组元素支持数值近似比较() {
        // Go json.Marshal 对 float 数组会丢 .0，期望 [1.0,2.0] 时应判定相等
        assertTrue(JudgeOutputMatcher.outputMatches("[1,2]", "[1.0,2.0]"));
        assertTrue(JudgeOutputMatcher.outputMatches("[1.5,2.0]", "[1.5,2]"));
    }

    @Test
    void 数组无序比较() {
        assertTrue(JudgeOutputMatcher.outputMatches("[1,2,3]", "[3,2,1]"));
        assertTrue(JudgeOutputMatcher.outputMatches("[1.0,2.0]", "[2,1]"));
        assertFalse(JudgeOutputMatcher.outputMatches("[1,2]", "[1,2,3]"));
        assertFalse(JudgeOutputMatcher.outputMatches("[1,2]", "[1,3]"));
    }

    @Test
    void 字符串数组不与数字数组误配() {
        assertFalse(JudgeOutputMatcher.outputMatches("[1,2]", "[\"1\",\"2\"]"));
    }
}
