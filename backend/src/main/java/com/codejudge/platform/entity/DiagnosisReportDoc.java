package com.codejudge.platform.entity;

import com.codejudge.platform.dto.DiagnosisReport;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教学诊断报告持久化实体，对应 MongoDB 集合 {@code diagnosis_reports}。
 *
 * <p>以考试 ID 作为文档主键，一场考试最多保存一份报告，重复保存即覆盖（upsert）。
 * 报告在考试结束后由定时任务自动生成，教师也可在报告页手动「重新生成」覆盖。</p>
 */
@Document(collection = "diagnosis_reports")
public class DiagnosisReportDoc {

    /** 报告主键，等于考试 ID（对应 exams 集合的 _id）。 */
    @Id
    private String id;

    /** 总体结论 */
    private String conclusion;

    /** 薄弱知识点维度 */
    private List<String> weakDimensions;

    /** 薄弱题目 */
    private List<String> weakQuestions;

    /** 重点关注（低分）学生 */
    private List<String> lowScoreStudents;

    /** 教学建议 */
    private List<String> suggestions;

    /** 报告生成时间 */
    private LocalDateTime generatedAt;

    public DiagnosisReportDoc() {
    }

    /** 由诊断报告 DTO 构造持久化文档。 */
    public static DiagnosisReportDoc of(String examId, DiagnosisReport report, LocalDateTime generatedAt) {
        DiagnosisReportDoc doc = new DiagnosisReportDoc();
        doc.id = examId;
        doc.conclusion = report.conclusion();
        doc.weakDimensions = report.weakDimensions();
        doc.weakQuestions = report.weakQuestions();
        doc.lowScoreStudents = report.lowScoreStudents();
        doc.suggestions = report.suggestions();
        doc.generatedAt = generatedAt;
        return doc;
    }

    /** 转回诊断报告 DTO。 */
    public DiagnosisReport toReport() {
        return new DiagnosisReport(conclusion, weakDimensions, weakQuestions, lowScoreStudents, suggestions);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public List<String> getWeakDimensions() {
        return weakDimensions;
    }

    public void setWeakDimensions(List<String> weakDimensions) {
        this.weakDimensions = weakDimensions;
    }

    public List<String> getWeakQuestions() {
        return weakQuestions;
    }

    public void setWeakQuestions(List<String> weakQuestions) {
        this.weakQuestions = weakQuestions;
    }

    public List<String> getLowScoreStudents() {
        return lowScoreStudents;
    }

    public void setLowScoreStudents(List<String> lowScoreStudents) {
        this.lowScoreStudents = lowScoreStudents;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
