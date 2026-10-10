package com.codejudge.platform.service;

import java.util.List;

/**
 * 解析后的方法签名（语言无关的统一结构）。
 *
 * <p>{@code returnType}/{@code paramTypes} 的含义随语言而不同：
 * Java/Go 为类型串（如 {@code int[]}、{@code []int}、{@code List<List<Integer>>}），
 * Python 为类型注解（如 {@code List[int]}、{@code Optional[ListNode]}）。</p>
 *
 * @param returnType 返回类型
 * @param methodName 方法名
 * @param paramTypes 参数类型列表（有序）
 */
public record MethodSignature(String returnType, String methodName, List<String> paramTypes) {
}
