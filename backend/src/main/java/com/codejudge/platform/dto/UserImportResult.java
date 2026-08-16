package com.codejudge.platform.dto;

import java.util.List;

/**
 * CSV 用户导入结果。
 *
 * @param total        实际数据行数
 * @param successCount 成功入库行数
 * @param failedCount  失败行数
 * @param errors       失败行明细
 */
public record UserImportResult(
        int total,
        int successCount,
        int failedCount,
        List<UserImportError> errors) {
}
