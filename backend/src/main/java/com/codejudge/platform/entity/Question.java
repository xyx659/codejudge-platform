package com.codejudge.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目实体，对应 MongoDB 集合 {@code questions}。
 *
 * <p>采用文档存储，便于灵活扩展题型与字段；
 * 测试用例内嵌在 {@link #testCases} 中。</p>
 */
@Document(collection = "questions")
public class Question {

    /** 题目 ID（MongoDB 自动生成的字符串主键） */
    @Id
    private String id;

    /** 题目标题 */
    private String title;

    /** 题目描述 */
    private String description;

    /** 需要实现的方法名（如 sum），用于生成评测模板 */
    private String methodName;

    /** 判题模式：METHOD（方法题，判题侧生成 Main 包装）/ STDIO（完整程序读标准输入输出） */
    private String judgeMode = "METHOD";

    /** 方法签名，自包含形式，如「int sum(int, int)」；STDIO 模式下可为空 */
    private String methodSignature;

    /** 编程语言（如 Java） */
    private String language;

    /** 难度：简单 / 中等 / 困难 */
    private String difficulty;

    /** 标签列表（如 数学、基础） */
    private List<String> tags = new ArrayList<String>();

    /** 测试用例列表（输入 + 期望输出） */
    private List<QuestionTestCase> testCases = new ArrayList<QuestionTestCase>();

    /** 是否已发布（仅发布后的题目对学生可见） */
    private Boolean published = false;

    /** 来源平台：CODEFORCES / LEETCODE，手工题可为空 */
    private String sourcePlatform;

    /** 来源平台内的稳定 ID，如 Codeforces 的 contestId+index、LeetCode 的 titleSlug */
    private String sourceId;

    /** 原题链接 */
    private String sourceUrl;

    /** 来源平台返回的原始元数据，便于排查和后续同步 */
    private Map<String, Object> sourceMetadata = new LinkedHashMap<String, Object>();

    /** 创建时间 */
    private LocalDateTime createdAt = LocalDateTime.now();

    public Question() {
    }

    public Question(String title, String description, String methodName) {
        this.title = title;
        this.description = description;
        this.methodName = methodName;
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

    public String getJudgeMode() {
        return judgeMode;
    }

    public void setJudgeMode(String judgeMode) {
        this.judgeMode = judgeMode;
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

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Map<String, Object> getSourceMetadata() {
        return sourceMetadata;
    }

    public void setSourceMetadata(Map<String, Object> sourceMetadata) {
        this.sourceMetadata = sourceMetadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
