package com.codejudge.platform.dto;

import java.util.List;

/**
 * JSON 题目模板导入结果。
 */
public record QuestionImportResult(
        int total,
        int successCount,
        int failedCount,
        List<QuestionImportError> errors) {
}
