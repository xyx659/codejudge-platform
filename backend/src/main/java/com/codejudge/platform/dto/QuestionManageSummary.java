package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Question;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端题目列表项。
 */
public record QuestionManageSummary(
        String id,
        String title,
        String difficulty,
        String language,
        String methodName,
        List<String> tags,
        Boolean published,
        String sourcePlatform,
        String sourceUrl,
        LocalDateTime createdAt) {

    public static QuestionManageSummary from(Question question) {
        return new QuestionManageSummary(
                question.getId(),
                question.getTitle(),
                question.getDifficulty(),
                question.getLanguage(),
                question.getMethodName(),
                question.getTags(),
                question.getPublished(),
                question.getSourcePlatform(),
                question.getSourceUrl(),
                question.getCreatedAt());
    }
}
