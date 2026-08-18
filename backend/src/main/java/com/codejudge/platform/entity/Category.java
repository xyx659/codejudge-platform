package com.codejudge.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 题目分类实体，对应 MongoDB 集合 {@code categories}。
 *
 * <p>分类用来把题库和考试按主题（如「基础语法」「算法」「数据结构」）归类。
 * 一个分类下可以有多个题目和多场考试。</p>
 *
 * <p>说明：分类与考试这两个 M3 教师端的新对象<b>存入 MongoDB</b>（而非 MySQL），
 * 是为了不修改团队已经写定的 {@code schema.sql}，做到「教师端代码完全独立、零侵入」。
 * 这也和题目（questions 集合）放在 MongoDB 的做法保持一致。</p>
 */
@Document(collection = "categories")
public class Category {

    /** 分类 ID（MongoDB 自动生成的字符串主键） */
    @Id
    private String id;

    /** 分类名称，如「基础语法」 */
    private String name;

    /** 分类描述，可选 */
    private String description;

    /** 排序号，数字越小越靠前，用于列表展示顺序 */
    private Integer sortOrder = 0;

    public Category() {
    }

    public Category(String name, String description, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
