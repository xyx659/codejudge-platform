package com.codejudge.platform.dto;

import com.codejudge.platform.entity.QuestionTestCase;

import java.util.List;

/**
 * 题目新增 / 修改请求体。
 *
 * @param title            题目标题
 * @param description      题目描述
 * @param methodName       需要实现的方法名（如 sum）
 * @param methodSignature  方法签名（如 int[] twoSum(int[], int)），容器判题生成 Main 包装类时使用
 * @param judgeMode        判题模式：METHOD / DESIGN / STDIO
 * @param designMethods    设计题方法定义列表（仅 DESIGN 模式），每项如 "void put(int,int)"
 * @param difficulty       难度：简单 / 中等 / 困难
 * @param categoryId       所属分类 ID（对应 categories._id），可选
 * @param tags             标签列表
 * @param testCases        测试用例列表（名称 + 输入 + 期望输出）
 * @param published        是否发布（发布后学生才可见）
 */
public record QuestionRequest(
        String title,
        String description,
        String methodName,
        String methodSignature,
        String judgeMode,
        List<String> designMethods,
        String difficulty,
        String categoryId,
        List<String> tags,
        List<QuestionTestCase> testCases,
        Boolean published) {
}
