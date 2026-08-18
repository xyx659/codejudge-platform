package com.codejudge.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试新增 / 修改请求体。
 *
 * @param title           考试标题
 * @param description     考试说明，可选
 * @param categoryId      所属分类 ID，可选
 * @param startTime       考试开始时间
 * @param endTime         考试结束时间
 * @param durationMinutes 考试时长（分钟）
 * @param passScore       及格分
 * @param targetClass     目标班级（文本标签）
 * @param questions       组卷题目列表（题目 ID + 分值）
 */
public record ExamRequest(
        String title,
        String description,
        String categoryId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,
        Integer passScore,
        String targetClass,
        List<ExamQuestionItem> questions) {
}
