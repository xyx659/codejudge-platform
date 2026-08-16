package com.codejudge.platform.common;

import java.util.List;

/**
 * 分页结果包装类。
 *
 * <p>列表类接口都需要「分页」，所以把「一页数据 + 分页信息」打包在一起返回。
 * 前端拿到后，用 {@code total} 除以 {@code size} 就能算出总页数。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code list} —— 当前这一页的数据（比如 10 道题目）</li>
 *   <li>{@code page} —— 当前页码，从 0 开始（第 0 页 = 第一页）</li>
 *   <li>{@code size} —— 每页有多少条</li>
 *   <li>{@code total} —— 符合条件的数据一共有多少条（用于算总页数）</li>
 * </ul>
 *
 * @param <T> 列表中每一项的类型（比如题目就是 QuestionSummary）
 */
public record PageResult<T>(List<T> list, int page, int size, long total) {
}
