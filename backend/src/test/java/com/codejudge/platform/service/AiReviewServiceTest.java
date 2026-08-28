package com.codejudge.platform.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    @Mock
    private SystemConfigService systemConfigService;

    @Test
    void AI服务读取解密Key但日志不输出Key() {
        String secretKey = "sk-secret-abcdef123456";
        when(systemConfigService.getAiRuntimeConfig())
                .thenReturn(new AiRuntimeConfig(
                        "DEEPSEEK",
                        "deepseek-chat",
                        "https://api.deepseek.com",
                        secretKey));
        AiReviewService aiReviewService = new AiReviewService(systemConfigService, new ObjectMapper());
        ListAppender<ILoggingEvent> appender = attachLogger();
        try {
            aiReviewService.logCurrentConfig(88L);
        } finally {
            detachLogger(appender);
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertEquals(secretKey, aiReviewService.currentConfig().apiKey(),
                "内部 AI 配置应返回已解密 Key");
        assertTrue(logs.contains("provider=DEEPSEEK"), "AI 日志应包含服务商");
        assertTrue(logs.contains("model=deepseek-chat"), "AI 日志应包含模型");
        assertFalse(logs.contains(secretKey), "AI 日志绝不能包含完整 API Key");
    }

    private ListAppender<ILoggingEvent> attachLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(AiReviewService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogger(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(AiReviewService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
