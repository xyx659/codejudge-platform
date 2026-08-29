package com.codejudge.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 教师端题目视图，对应 MongoDB 集合 {@code questions}（与团队的 {@link Question} 同一集合）。
 *
 * <p>为什么单独建这个类、而不直接改团队的 {@link Question}：</p>
 * <ul>
 *   <li>题目管理需要一个「分类」字段 {@link #categoryId}，而团队的 {@link Question}
 *       没有该字段；为了<b>不改动团队已写定的代码</b>，这里新建一个带 {@code categoryId}
 *       的视图类，映射到同一个 questions 集合。</li>
 *   <li>MongoDB 是无 Schema 的，同一个集合里既可以是学生端写的 {@link Question}（无分类），
 *       也可以是教师端写的本类（带分类），二者互不影响。</li>
 * </ul>
 *
 * <p>教师端题库的全部读写统一走本类 + {@code MongoTemplate}，学生端的 {@link Question}
 * 及其仓库保持不变。</p>
 */
@Document(collection = "questions")
public class TeacherQuestion {

    /** 题目 ID（MongoDB 自动生成的字符串主键） */
    @Id
    private String id;

    /** 题目标题 */
    private String title;

    /** 题目描述 */
    private String description;

    /** 需要实现的方法名（如 sum） */
    private String methodName;

    /** 方法签名（如 int[] twoSum(int[], int)），容器判题生成 Main 包装类时使用 */
    private String methodSignature;

    /** 编程语言（如 Java） */
    private String language;

    /** 难度：简单 / 中等 / 困难 */
    private String difficulty;

    /** 标签列表 */
    private List<String> tags = new ArrayList<String>();

    /** 测试用例列表（输入 + 期望输出） */
    private List<QuestionTestCase> testCases = new ArrayList<QuestionTestCase>();

    /** 是否已发布（仅发布后的题目对学生可见） */
    private Boolean published = false;

    /** 所属分类 ID（对应 categories._id），可选 */
    private String categoryId;

    /** 创建时间 */
    private LocalDateTime createdAt = LocalDateTime.now();

    public TeacherQuestion() {
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<QuestionTestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<QuestionTestCase> testCases) {
        this.testCases = testCases;
    }

    public Boolean getPublished() {
        return published;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
