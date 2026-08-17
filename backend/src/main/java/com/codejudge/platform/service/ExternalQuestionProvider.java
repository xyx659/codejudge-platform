package com.codejudge.platform.service;

import com.codejudge.platform.dto.ExternalQuestionCandidate;

import java.util.List;

/**
 * 外部题目平台 Provider。
 */
public interface ExternalQuestionProvider {

    String platform();

    List<ExternalQuestionCandidate> search(
            String keyword,
            String difficulty,
            int page,
            int size);

    ExternalQuestionCandidate getDetail(String sourceId);
}
