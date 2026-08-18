package com.codejudge.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 考试实体，对应 MongoDB 集合 {@code exams}。
 *
 * <p>考试 = 一组题目（组卷）+ 考试时间、及格分、目标班级等元信息。
 * 题目列表 {@link #questions} 内嵌了组卷时的快照（标题/难度/分值），
 * 发布后不会被题目的后续编辑影响。</p>
 *
 * <p>状态流转：{@code DRAFT}（草稿，可编辑）→ {@code PUBLISHED}（已发布，学生可考）
 * → {@code CLOSED}（已结束，进入成绩分析阶段）。</p>
 */
@Document(collection = "exams")
public class Exam {

    /** 考试 ID（MongoDB 自动生成的字符串主键） */
    @Id
    private String id;

    /** 考试标题 */
    private String title;

    /** 考试说明，可选 */
    private String description;

    /** 所属分类 ID（对应 categories._id），可选 */
    private String categoryId;

    /** 考试开始时间 */
    private LocalDateTime startTime;

    /** 考试结束时间 */
    private LocalDateTime endTime;

    /** 考试时长（分钟），冗余记录，便于前端展示 */
    private Integer durationMinutes;

    /** 及格分 */
    private Integer passScore;

    /** 目标班级（文本标签，如「软件工程2101班」） */
    private String targetClass;

    /** 状态：DRAFT / PUBLISHED / CLOSED */
    private String status = "DRAFT";

    /** 组卷题目列表（含每题分值与标题/难度快照） */
    private List<ExamQuestion> questions = new ArrayList<ExamQuestion>();

    /** 创建时间 */
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 最后修改时间 */
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Exam() {
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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getPassScore() {
        return passScore;
    }

    public void setPassScore(Integer passScore) {
        this.passScore = passScore;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ExamQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<ExamQuestion> questions) {
        this.questions = questions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
