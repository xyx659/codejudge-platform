package com.codejudge.platform.dto;

import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.entity.TeacherQuestion;

import java.util.List;

/**
 * 教师端题目详情（完整信息，供编辑表单回显）。
 *
 * @param id              题目 ID
 * @param title           题目标题
 * @param description     题目描述
 * @param methodName      方法名
 * @param methodSignature 方法签名（如 int[] twoSum(int[], int)），容器判题使用
 * @param language        编程语言
 * @param difficulty      难度
 * @param tags            标签列表
 * @param testCases       测试用例列表
 * @param categoryId      所属分类 ID
 * @param published       是否已发布
 */
public record TeacherQuestionDetail(
        String id,
        String title,
        String description,
        String methodName,
        String methodSignature,
        String language,
        String difficulty,
        List<String> tags,
        List<QuestionTestCase> testCases,
        String categoryId,
        Boolean published) {

    /** 工厂方法：把教师端题目实体转成完整详情 */
    public static TeacherQuestionDetail from(TeacherQuestion q) {
        return new TeacherQuestionDetail(
                q.getId(),
                q.getTitle(),
                q.getDescription(),
                q.getMethodName(),
                q.getMethodSignature(),
                q.getLanguage(),
                q.getDifficulty(),
                q.getTags(),
                q.getTestCases(),
                q.getCategoryId(),
                q.getPublished());
    }
}
