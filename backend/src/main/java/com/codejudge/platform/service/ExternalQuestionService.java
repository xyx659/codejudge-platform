package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExternalQuestionCandidate;
import com.codejudge.platform.dto.QuestionManageDetail;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外部题目搜索与导入服务。
 */
@Service
public class ExternalQuestionService {

    private final Map<String, ExternalQuestionProvider> providers;
    private final QuestionService questionService;

    public ExternalQuestionService(
            List<ExternalQuestionProvider> providerList,
            QuestionService questionService) {
        this.providers = providerList.stream().collect(Collectors.toMap(
                provider -> provider.platform().toUpperCase(Locale.ROOT),
                Function.identity()));
        this.questionService = questionService;
    }

    public PageResult<ExternalQuestionCandidate> search(
            String platform,
            String keyword,
            String difficulty,
            int page,
            int size) {
        ExternalQuestionProvider provider = provider(platform);
        List<ExternalQuestionCandidate> list = provider.search(
                keyword,
                difficulty,
                page,
                size);
        return new PageResult<>(list, page, size, list.size());
    }

    public QuestionManageDetail importQuestion(
            String platform,
            String sourceId) {
        ExternalQuestionProvider provider = provider(platform);
        ExternalQuestionCandidate candidate = provider.getDetail(sourceId);
        return questionService.importExternalCandidate(candidate);
    }

    private ExternalQuestionProvider provider(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new BadRequestException("请选择题目平台");
        }
        ExternalQuestionProvider provider = providers.get(
                platform.trim().toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new BadRequestException("不支持的题目平台：" + platform);
        }
        return provider;
    }
}
