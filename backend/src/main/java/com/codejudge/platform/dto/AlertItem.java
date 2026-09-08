package com.codejudge.platform.dto;

import java.time.LocalDateTime;

/**
 * 监考预警条目。
 *
 * @param studentId 学生 ID
 * @param name      学生姓名
 * @param type      预警类型（如「未开始」「零分题」）
 * @param message   预警说明
 * @param time      预警触发时间（切屏/切页面取最近一次事件时间，状态类预警取当前时间）
 */
public record AlertItem(Long studentId, String name, String type, String message, LocalDateTime time) {
}
