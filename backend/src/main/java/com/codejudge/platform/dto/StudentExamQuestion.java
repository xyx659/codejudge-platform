package com.codejudge.platform.dto;

import com.codejudge.platform.entity.QuestionTestCase;

import java.util.List;

/**
 * 学生端考试详情里的一道题。
 *
 * <p>除了组卷快照（标题/难度/分值），还<b>展开</b>了题目的完整内容（描述/方法名/语言/测试用例），
 * 供学生直接在试卷内看清题目并作答。</p>
 *
 * <p>{@link #sourceCode}/{@link #judgeStatus}/{@link #myScore} 三个字段只在学生
 * <b>已交卷后回看</b>时才有值，未交卷或考试未结束时为 {@code null}。</p>
 *
 * @param questionId  题目 ID（questions._id）
 * @param title       题目标题
 * @param difficulty  难度
 * @param score       本题分值
 * @param description 题目描述
 * @param methodName  需实现的方法名
 * @param language    编程语言
 * @param testCases   测试用例（含期望输出）
 * @param sourceCode  学生已交的源码（未交为 null）
 * @param judgeStatus 判卷状态（未交为 null）
 * @param myScore     学生本题得分（未交为 null）
 */
public record StudentExamQuestion(
        String questionId,
        String title,
        String difficulty,
        Integer score,
        String description,
        String methodName,
        String language,
        List<QuestionTestCase> testCases,
        String sourceCode,
        String judgeStatus,
        Integer myScore) {
}
