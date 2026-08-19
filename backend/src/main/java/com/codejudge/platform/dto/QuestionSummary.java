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
 * @param submitted  当前学生是否已提交过此题（用于「每题限一次」标记）
 */
public record QuestionSummary(
        String id,
        String title,
        String difficulty,
        String language,
        String methodName,
        List<String> tags,
        boolean submitted) {

    /**
     * 工厂方法：把一个 MongoDB 的 {@link Question} 实体，转换成列表摘要。
     *
     * <p>「工厂方法」就是一个专门用来「造对象」的静态方法。
     * 这里把题目实体里前端列表需要的字段挑出来，测试用例等敏感字段直接丢掉。
     * 不关心提交状态时可调用此方法，默认 {@code submitted = false}。</p>
     */
    public static QuestionSummary from(Question q) {
        return from(q, false);
    }

    /**
     * 工厂方法：附带「是否已提交」标记。
     *
     * @param q         题目实体
     * @param submitted 当前学生是否已提交过此题
     */
    public static QuestionSummary from(Question q, boolean submitted) {
        return new QuestionSummary(
                q.getId(),
                q.getTitle(),
                q.getDifficulty(),
                q.getLanguage(),
                q.getMethodName(),
                q.getTags(),
                submitted);
    }
}
