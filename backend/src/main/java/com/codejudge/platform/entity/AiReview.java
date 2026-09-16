package com.codejudge.platform.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 评审报告，内嵌于 {@link SubmissionDetail#aiReview}。
 *
 * <p>在黑盒判题之外，由大模型对代码质量做白盒分析。</p>
 */
public class AiReview {

    /** 综合得分 */
    private Integer score;

    /** 用例通过率（0~100） */
    private Integer passRate;

    /** 代码质量分（0~100，白盒分析） */
    private Integer qualityScore;

    /** 评审反馈列表 */
    private List<String> feedback = new ArrayList<String>();

    /** 评分说明（AI 对质量分的文字解释） */
    private String scoreExplanation;

    /** 时间复杂度（如 O(n)） */
    private String timeComplexity;

    /** 空间复杂度（如 O(1)） */
    private String spaceComplexity;

    public AiReview() {
    }

    public AiReview(Integer score, Integer passRate, Integer qualityScore, List<String> feedback) {
        this.score = score;
        this.passRate = passRate;
        this.qualityScore = qualityScore;
        this.feedback = feedback;
    }

    public AiReview(Integer score, Integer passRate, Integer qualityScore, List<String> feedback,
                    String scoreExplanation, String timeComplexity, String spaceComplexity) {
        this.score = score;
        this.passRate = passRate;
        this.qualityScore = qualityScore;
        this.feedback = feedback;
        this.scoreExplanation = scoreExplanation;
        this.timeComplexity = timeComplexity;
        this.spaceComplexity = spaceComplexity;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getPassRate() {
        return passRate;
    }

    public void setPassRate(Integer passRate) {
        this.passRate = passRate;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public List<String> getFeedback() {
        return feedback;
    }

    public void setFeedback(List<String> feedback) {
        this.feedback = feedback;
    }

    public String getScoreExplanation() {
        return scoreExplanation;
    }

    public void setScoreExplanation(String scoreExplanation) {
        this.scoreExplanation = scoreExplanation;
    }

    public String getTimeComplexity() {
        return timeComplexity;
    }

    public void setTimeComplexity(String timeComplexity) {
        this.timeComplexity = timeComplexity;
    }

    public String getSpaceComplexity() {
        return spaceComplexity;
    }

    public void setSpaceComplexity(String spaceComplexity) {
        this.spaceComplexity = spaceComplexity;
    }
}
