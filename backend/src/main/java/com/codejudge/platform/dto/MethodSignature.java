package com.codejudge.platform.dto;

import java.util.List;

/**
 * 方法签名（语言无关）：从题目的 methodSignature 字符串解析而来。
 *
 * <p>数据库中签名统一用 Java 类型记号存储（如 {@code int[]}、{@code List<Integer>}、
 * {@code TreeNode}、{@code Node}），各语言的 {@code LanguageHandler} 负责把它映射成
 * 自己语言的类型表示。例如 {@code int[] twoSum(int[] nums, int target)} 解析为：
 * {@code returnType="int[]", methodName="twoSum", paramTypes=["int[]","int"]}。</p>
 */
public record MethodSignature(String returnType, String methodName, List<String> paramTypes) {
}
