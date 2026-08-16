package com.codejudge.platform.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Mock
    private SystemConfigService systemConfigService;

    @Test
    void 触发评测时读取并记录最新配置且不记录AIKey() {
        when(systemConfigService.getJudgeRuntimeConfig())
                .thenReturn(new JudgeRuntimeConfig(4200, 512, 8));
        JudgeService judgeService = new JudgeService(systemConfigService);

        ListAppender<ILoggingEvent> appender = attachLogger();
        try {
            judgeService.trigger(100L);
        } finally {
            detachLogger(appender);
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        verify(systemConfigService).getJudgeRuntimeConfig();
        assertTrue(logs.contains("timeoutMs=4200"), "日志应包含最新评测超时");
        assertTrue(logs.contains("memoryMb=512"), "日志应包含最新内存限制");
        assertTrue(logs.contains("maxConcurrent=8"), "日志应包含最新最大并发");
        assertFalse(logs.toLowerCase().contains("apikey"), "评测日志不应包含 AI Key");
    }

    private ListAppender<ILoggingEvent> attachLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(JudgeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogger(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(JudgeService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
