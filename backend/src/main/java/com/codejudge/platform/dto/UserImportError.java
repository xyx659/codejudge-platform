package com.codejudge.platform.dto;

/**
 * CSV 导入失败行的错误信息。
 *
 * @param row      失败数据所在的 CSV 物理行号，表头为第 1 行
 * @param username 该行解析出的用户名，字段为空时为 null
 * @param reason   失败原因
 */
public record UserImportError(int row, String username, String reason) {
}
