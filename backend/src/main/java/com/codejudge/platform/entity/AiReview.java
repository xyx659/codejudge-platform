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

    public AiReview() {
    }

    public AiReview(Integer score, Integer passRate, Integer qualityScore, List<String> feedback) {
        this.score = score;
        this.passRate = passRate;
        this.qualityScore = qualityScore;
        this.feedback = feedback;
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
}
