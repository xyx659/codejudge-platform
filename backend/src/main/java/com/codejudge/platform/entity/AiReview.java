package com.codejudge.platform.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 评审报告，内嵌于 {@link SubmissionDetail#aiReview}。
 *
 * <p>在黑盒判题之外，由大模型对代码质量做白盒分析。</p>
 */
public class AiReview {

    /** 综合得分（加权计算） */
    private Integer score;

    /** 用例通过率（0~100） */
    private Integer passRate;

    /** 代码质量分（0~100，白盒分析） */
    private Integer qualityScore;

    /** 评审反馈列表 */
    private List<String> feedback = new ArrayList<String>();

    /** 时间复杂度（如 O(n)、O(log n)） */
    private String timeComplexity;

    /** 空间复杂度（如 O(1)、O(n)） */
    private String spaceComplexity;

    /** 各维度评分（维度ID → 分数0-100） */
    private Map<String, Integer> dimensionScores = new LinkedHashMap<String, Integer>();

    /** 总评语 */
    private String summary;

    public AiReview() {
    }

    public AiReview(Integer score, Integer passRate, Integer qualityScore, List<String> feedback,
                    String timeComplexity, String spaceComplexity,
                    Map<String, Integer> dimensionScores, String summary) {
        this.score = score;
        this.passRate = passRate;
        this.qualityScore = qualityScore;
        this.feedback = feedback;
        this.timeComplexity = timeComplexity;
        this.spaceComplexity = spaceComplexity;
        this.dimensionScores = dimensionScores;
        this.summary = summary;
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

    public Map<String, Integer> getDimensionScores() {
        return dimensionScores;
    }

    public void setDimensionScores(Map<String, Integer> dimensionScores) {
        this.dimensionScores = dimensionScores;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
