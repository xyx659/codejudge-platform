package com.codejudge.platform.dto;

/**
 * 学生端上报防作弊事件的请求体。
 *
 * @param eventType 事件类型：{@code SWITCH_TAB}（切屏）/ {@code LEAVE_PAGE}（切页面）
 */
public record CheatEventRequest(String eventType) {
}
