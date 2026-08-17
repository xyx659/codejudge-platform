package com.codejudge.platform.dto;

/**
 * 监考预警条目。
 *
 * @param studentId 学生 ID
 * @param name      学生姓名
 * @param type      预警类型（如「未开始」「零分题」）
 * @param message   预警说明
 */
public record AlertItem(Long studentId, String name, String type, String message) {
}
