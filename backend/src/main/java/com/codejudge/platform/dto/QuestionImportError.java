package com.codejudge.platform.dto;

/**
 * JSON 模板导入失败题目信息。
 */
public record QuestionImportError(
        int row,
        String title,
        String reason) {
}
