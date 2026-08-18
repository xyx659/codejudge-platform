package com.codejudge.platform.dto;

/**
 * 分类新增 / 修改请求体。
 *
 * @param name        分类名称，必填
 * @param description 分类描述，可选
 * @param sortOrder   排序号，数字越小越靠前，可选（默认 0）
 */
public record CategoryRequest(String name, String description, Integer sortOrder) {
}
