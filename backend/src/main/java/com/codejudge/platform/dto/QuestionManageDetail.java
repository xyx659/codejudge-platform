package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端题目详情，包含测试用例和来源信息。
 */
public record QuestionManageDetail(
        String id,
        String title,
        String description,
        String methodName,
        String language,
        String difficulty,
        List<String> tags,
        Boolean published,
        List<QuestionTestCase> testCases,
        String sourcePlatform,
        String sourceId,
        String sourceUrl,
        Map<String, Object> sourceMetadata,
        LocalDateTime createdAt) {

    public static QuestionManageDetail from(Question question) {
        return new QuestionManageDetail(
                question.getId(),
                question.getTitle(),
                question.getDescription(),
                question.getMethodName(),
                question.getLanguage(),
                question.getDifficulty(),
                question.getTags(),
                question.getPublished(),
                question.getTestCases(),
                question.getSourcePlatform(),
                question.getSourceId(),
                question.getSourceUrl(),
                question.getSourceMetadata(),
                question.getCreatedAt());
    }
}
