package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 语言处理器注册表：收集所有 {@link LanguageHandler} 实现，按题目 {@code language} 路由。
 *
 * <p>判题编排（{@link DockerJudgeEngine} / {@link RunService}）只依赖本类取处理器，不再直接持有
 * 具体语言实现。</p>
 */
@Component
public class LanguageHandlerRegistry {

    /** 语言名（小写）→ 处理器。 */
    private final Map<String, LanguageHandler> handlers;

    public LanguageHandlerRegistry(List<LanguageHandler> handlerList) {
        Map<String, LanguageHandler> map = new HashMap<>();
        for (LanguageHandler handler : handlerList) {
            map.put(handler.language().toLowerCase(Locale.ROOT), handler);
        }
        this.handlers = Map.copyOf(map);
    }

    /**
     * 按题目语言取处理器。
     *
     * <p>{@code null}/空串按历史数据回退到 Java；显式设置了不支持的语言（如 C++）则抛
     * {@link BadRequestException}，避免被静默误判。</p>
     */
    public LanguageHandler get(String language) {
        String key = language == null || language.isBlank()
                ? "java"
                : language.trim().toLowerCase(Locale.ROOT);
        LanguageHandler handler = handlers.get(key);
        if (handler == null) {
            throw new BadRequestException("暂不支持的编程语言：" + language);
        }
        return handler;
    }
}
