package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExamAnalytics;
import com.codejudge.platform.dto.StudentExamAnswer;
import com.codejudge.platform.dto.StudentScoreItem;
import com.codejudge.platform.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师端学情分析接口（对外地址都以 {@code /api/teacher/analytics} 开头）。
 */
@RestController
@RequestMapping("/api/teacher/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** 一场考试的学情分析（成绩统计 + 分数段分布 + 逐题掌握度） */
    @GetMapping("/{examId}")
    public ApiResponse<ExamAnalytics> analyze(@PathVariable String examId) {
        return ApiResponse.ok(analyticsService.analyze(examId));
    }

    /** 分页查询一场考试的学生成绩明细（关键字 / 班级 / 及格筛选） */
    @GetMapping("/{examId}/students")
    public ApiResponse<PageResult<StudentScoreItem>> students(
            @PathVariable String examId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) Boolean passed) {
        return ApiResponse.ok(analyticsService.listStudentScores(
                examId, page, size, keyword, className, passed));
    }

    /** 查询某学生在一场考试里的逐题答卷 */
    @GetMapping("/{examId}/students/{studentId}/answers")
    public ApiResponse<List<StudentExamAnswer>> studentAnswers(
            @PathVariable String examId,
            @PathVariable Long studentId) {
        return ApiResponse.ok(analyticsService.studentAnswers(examId, studentId));
    }
}
