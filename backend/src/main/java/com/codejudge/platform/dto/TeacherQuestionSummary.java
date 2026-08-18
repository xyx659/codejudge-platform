package com.codejudge.platform.dto;

import com.codejudge.platform.entity.TeacherQuestion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师端题库列表项（摘要）。
 *
 * <p>列表只返回摘要字段，<b>不返回题目描述和测试用例</b>（那些在详情接口单独给），
 * 这样列表接口更轻量。</p>
 *
 * @param id         题目 ID
 * @param title      题目标题
 * @param difficulty 难度
 * @param language   编程语言
 * @param methodName 方法名
 * @param tags       标签列表
 * @param categoryId 所属分类 ID
 * @param published  是否已发布
 * @param createdAt  创建时间
 */
public record TeacherQuestionSummary(
        String id,
        String title,
        String difficulty,
        String language,
        String methodName,
        List<String> tags,
        String categoryId,
        Boolean published,
        LocalDateTime createdAt) {

    /** 工厂方法：把教师端题目实体转成列表摘要 */
    public static TeacherQuestionSummary from(TeacherQuestion q) {
        return new TeacherQuestionSummary(
                q.getId(),
                q.getTitle(),
                q.getDifficulty(),
                q.getLanguage(),
                q.getMethodName(),
                q.getTags(),
                q.getCategoryId(),
                q.getPublished(),
                q.getCreatedAt());
    }
}
