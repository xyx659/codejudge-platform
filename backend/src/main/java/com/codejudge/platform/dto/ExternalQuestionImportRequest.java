package com.codejudge.platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 外部题目导入请求。
 */
public record ExternalQuestionImportRequest(
        @NotBlank(message = "平台不能为空") String platform,
        @NotBlank(message = "题目来源 ID 不能为空") String sourceId) {
}
