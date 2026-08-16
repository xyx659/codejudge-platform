package com.codejudge.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 评审服务占位实现。
 *
 * <p>当前只从系统配置读取模型信息，不发起外部 AI 调用。</p>
 */
@Service
public class AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewService.class);

    private final SystemConfigService systemConfigService;

    public AiReviewService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /** 读取当前 AI 运行配置，供后续 AI 评审实现使用 */
    public AiRuntimeConfig currentConfig() {
        return systemConfigService.getAiRuntimeConfig();
    }

    /** 记录当前模型配置，不输出 API Key */
    public void logCurrentConfig(Long submissionId) {
        AiRuntimeConfig config = currentConfig();
        log.info(
                "AI评审配置已加载：submissionId={}, provider={}, model={}, baseUrl={}, hasApiKey={}",
                submissionId,
                config.provider(),
                config.model(),
                config.baseUrl(),
                config.apiKey() != null && !config.apiKey().isBlank());
    }
}
