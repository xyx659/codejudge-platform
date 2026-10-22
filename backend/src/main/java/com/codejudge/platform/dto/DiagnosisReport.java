package com.codejudge.platform.dto;

import java.util.List;

/**
 * 教学诊断报告（规则化生成，不依赖大模型）。
 *
 * @param conclusion       总体结论（一段话，概括成绩与整体水平）
 * @param weakDimensions   薄弱维度（按得分率升序，Top N，格式「维度名（得分率 X%）」）
 * @param weakQuestions    薄弱题目（按得分率升序，Top N，格式「题目标题（得分率 X%）」）
 * @param lowScoreStudents 低分学生（未及格中得分最低的 Top N，格式「姓名（X 分）」）
 * @param suggestions      教学建议（若干条可执行的改进建议）
 */
public record DiagnosisReport(
        String conclusion,
        List<String> weakDimensions,
        List<String> weakQuestions,
        List<String> lowScoreStudents,
        List<String> suggestions) {
}
