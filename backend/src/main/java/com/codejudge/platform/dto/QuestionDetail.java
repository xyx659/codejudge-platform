package com.codejudge.platform.dto;

import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;

import java.util.List;

/**
 * 题目详情（完整信息）。
 *
 * <p>和列表用的 {@link QuestionSummary} 不同，详情会<b>带上题目描述和测试用例</b>，
 * 供学生看清题目要求、知道评测会用哪些输入输出。</p>
 *
 * @param id         题目 ID（对应 MongoDB 的 _id）
 * @param title      题目标题
 * @param description 题目描述（完整题目要求）
 * @param methodName 需要实现的方法名（如 sum）
 * @param language   编程语言（如 Java）
 * @param difficulty 难度：简单 / 中等 / 困难
 * @param tags       标签列表
 * @param testCases  测试用例列表（每个含名称、输入、期望输出）
 */
public record QuestionDetail(
        String id,
        String title,
        String description,
        String methodName,
        String language,
        String difficulty,
        List<String> tags,
        List<QuestionTestCase> testCases) {

    /**
     * 工厂方法：把 MongoDB 的 {@link Question} 实体转成详情 DTO。
     *
     * <p>测试用例 {@link QuestionTestCase} 本身已是简单数据类（名称/输入/期望输出），
     * 直接原样放进详情里返回即可。</p>
     */
    public static QuestionDetail from(Question q) {
        return new QuestionDetail(
                q.getId(),
                q.getTitle(),
                q.getDescription(),
                q.getMethodName(),
                q.getLanguage(),
                q.getDifficulty(),
                q.getTags(),
                q.getTestCases());
    }
}
