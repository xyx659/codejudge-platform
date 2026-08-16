package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Question;

import java.util.List;

/**
 * 题目列表项（摘要信息）。
 *
 * <p>列表接口<b>只返回摘要</b>，故意不返回「题目描述」和「测试用例」，
 * 这样学生在列表页拿不到答案和测试数据；点进详情页后，再由详情接口单独返回。</p>
 *
 * @param id         题目 ID（对应 MongoDB 的 _id）
 * @param title      题目标题
 * @param difficulty 难度：简单 / 中等 / 困难
 * @param language   编程语言（如 Java），学生写代码时要按这个语言来
 * @param methodName 需要实现的方法名（如 sum），学生写代码时方法名要对上
 * @param tags       标签列表（如 数学、基础）
 */
public record QuestionSummary(
        String id,
        String title,
        String difficulty,
        String language,
        String methodName,
        List<String> tags) {

    /**
     * 工厂方法：把一个 MongoDB 的 {@link Question} 实体，转换成列表摘要。
     *
     * <p>「工厂方法」就是一个专门用来「造对象」的静态方法。
     * 这里把题目实体里前端列表需要的字段挑出来，测试用例等敏感字段直接丢掉。</p>
     */
    public static QuestionSummary from(Question q) {
        return new QuestionSummary(
                q.getId(),
                q.getTitle(),
                q.getDifficulty(),
                q.getLanguage(),
                q.getMethodName(),
                q.getTags());
    }
}
