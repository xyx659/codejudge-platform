package com.codejudge.platform.entity;

/**
 * 考试中的一道题目（组卷条目），内嵌于 {@link Exam#questions}。
 *
 * <p>除了题目 ID 和这道题在考试里的分值，还<b>快照</b>了题目标题与难度。
 * 这样即使发布后教师又修改了题目标题/难度，已发布考试里展示的仍是组卷时的内容，
 * 实现「组卷即冻结」，避免已发布考试的内容被事后改动影响。</p>
 */
public class ExamQuestion {

    /** 题目 ID（对应 MongoDB questions._id） */
    private String questionId;

    /** 这道题在本场考试中的分值 */
    private Integer score;

    /** 题目标题（组卷时快照） */
    private String title;

    /** 题目难度（组卷时快照） */
    private String difficulty;

    public ExamQuestion() {
    }

    public ExamQuestion(String questionId, Integer score, String title, String difficulty) {
        this.questionId = questionId;
        this.score = score;
        this.title = title;
        this.difficulty = difficulty;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
