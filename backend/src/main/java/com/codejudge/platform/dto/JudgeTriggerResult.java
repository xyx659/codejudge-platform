package com.codejudge.platform.dto;

/**
 * 手动触发评测的结果（骨架）。
 *
 * @param submissionId 提交记录 ID（MySQL 的 submissions.id）
 * @param accepted     是否已受理触发
 * @param message      提示信息
 */
public record JudgeTriggerResult(
        Long submissionId,
        boolean accepted,
        String message) {
}
