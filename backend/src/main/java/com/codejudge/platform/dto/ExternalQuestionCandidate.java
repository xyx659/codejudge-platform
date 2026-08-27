package com.codejudge.platform.dto;

import java.util.List;
import java.util.Map;

/**
 * 外部题目搜索结果。
 */
public record ExternalQuestionCandidate(
        String sourcePlatform,
        String sourceId,
        String title,
        String description,
        String methodName,
        String methodSignature,
        String language,
        String difficulty,
        List<String> tags,
        List<QuestionTestCaseRequest> testCases,
        String sourceUrl,
        Map<String, Object> sourceMetadata) {
}
