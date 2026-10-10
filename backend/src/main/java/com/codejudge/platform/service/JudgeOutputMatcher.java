package com.codejudge.platform.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 判题输出比对工具（纯静态）。
 *
 * <p>供 {@link DockerJudgeEngine}（正式判题）与 {@link RunService}（样例自测）共用同一套
 * 比对逻辑，保证「测试」与「最终提交」看到的判卷结果一致。</p>
 */
public final class JudgeOutputMatcher {

    private JudgeOutputMatcher() {
    }

    /**
     * 智能输出比对：先精确匹配，失败后尝试浮点模糊比对和集合无序比对。
     */
    public static boolean outputMatches(String actual, String expected) {
        // 1. 精确匹配（最快路径）
        if (actual.equals(expected)) {
            return true;
        }
        // 2. 浮点模糊比对：两端都是单个数值时，允许 1e-5 误差
        if (isNumeric(actual) && isNumeric(expected) && numbersClose(actual, expected)) {
            return true;
        }
        // 3. 集合/数组无序比对：两端都是 [...] 格式时，排序后比较
        if (actual.startsWith("[") && expected.startsWith("[")) {
            return arrayMatches(actual, expected);
        }
        return false;
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 数组/集合无序比对：按逗号分割、排序后逐元素比较。
     * 处理嵌套数组时保持子数组原样，只对顶层排序。
     */
    private static boolean arrayMatches(String actual, String expected) {
        try {
            String[] aTokens = splitTopLevel(actual.substring(1, actual.length() - 1));
            String[] eTokens = splitTopLevel(expected.substring(1, expected.length() - 1));
            if (aTokens.length != eTokens.length) return false;
            // 先尝试有序比较（多数情况有序即可）
            if (tokensEqual(aTokens, eTokens)) return true;
            // 无序比较：排序后比较
            String[] aSorted = aTokens.clone();
            String[] eSorted = eTokens.clone();
            Arrays.sort(aSorted);
            Arrays.sort(eSorted);
            return tokensEqual(aSorted, eSorted);
        } catch (Exception e) {
            return false;
        }
    }

    /** 逐元素比较：数值元素允许 1e-5 浮点误差，其余按字符串精确比较。 */
    private static boolean tokensEqual(String[] a, String[] e) {
        for (int i = 0; i < a.length; i++) {
            if (!tokenMatches(a[i], e[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean tokenMatches(String a, String e) {
        if (a.equals(e)) {
            return true;
        }
        // 数值元素做浮点近似比较，避免 Go/Python/Java 浮点序列化差异（如 1.0 vs 1）误判
        if (isNumeric(a) && isNumeric(e)) {
            return numbersClose(a, e);
        }
        return false;
    }

    /** 数值近似相等：绝对误差或相对误差在 1e-5 内。 */
    private static boolean numbersClose(String a, String e) {
        try {
            double av = Double.parseDouble(a);
            double ev = Double.parseDouble(e);
            if (Math.abs(av - ev) < 1e-5) {
                return true;
            }
            if (ev != 0 && Math.abs((av - ev) / ev) < 1e-5) {
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    /** 按顶层逗号拆分，忽略嵌套括号内的逗号。 */
    private static String[] splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '[' || c == '(' || c == '{') depth++;
                else if (c == ']' || c == ')' || c == '}') depth--;
            }
            if (!inStr && depth == 0 && c == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }
}
