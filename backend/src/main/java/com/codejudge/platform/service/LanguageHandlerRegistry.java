package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 语言处理器注册表：自动收集所有 {@link LanguageHandler} Bean，按语言名路由。
 *
 * <p>各语言 Handler 只需各自注册为 Spring Bean 即可被收集，无需在此登记，避免多语言并行开发时
 * 都来改这个文件造成冲突。</p>
 */
@Service
public class LanguageHandlerRegistry {

    private final Map<String, LanguageHandler> handlers;

    public LanguageHandlerRegistry(List<LanguageHandler> all) {
        this.handlers = all.stream()
                .collect(Collectors.toMap(LanguageHandler::language, h -> h));
    }

    /** 按语言名取处理器；未知语言抛 400。 */
    public LanguageHandler get(String language) {
        LanguageHandler handler = handlers.get(language);
        if (handler == null) {
            throw new BadRequestException("暂不支持的编程语言：" + language);
        }
        return handler;
    }
}
