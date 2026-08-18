package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.dto.ExamAnalytics;
import com.codejudge.platform.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
