package com.codejudge.platform.dto;

/**
 * 代码提交响应体。
 *
 * <p>提交接口<b>不等待评测完成</b>，先把「提交 ID + 状态」返回给前端，
 * 前端据此显示「提交成功，评测中」；真正的成绩等评测完成后，再用查询接口获取。</p>
 *
 * @param submissionId 提交记录 ID（MySQL 的 submissions.id，后面查成绩要用它）
 * @param judgeStatus  判卷状态：PENDING / RUN_COMPLETED / COMPILE_ERROR / TIMEOUT
 */
public record SubmissionResponse(Long submissionId, String judgeStatus) {
}
