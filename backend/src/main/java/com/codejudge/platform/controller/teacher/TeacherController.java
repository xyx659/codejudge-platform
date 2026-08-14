package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 教师端接口。
 *
 * <p>当前为骨架占位实现，仅返回状态提示，具体业务待补充。</p>
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    /** 题库管理（占位） */
    @GetMapping("/questions")
    public ApiResponse<Map<String, Object>> questions() {
        return ApiResponse.ok(Map.of(
                "endpoint", "题库管理",
                "status", "基础框架已就绪"
        ));
    }

    /** 考试管理（占位） */
    @GetMapping("/exams")
    public ApiResponse<Map<String, Object>> exams() {
        return ApiResponse.ok(Map.of(
                "endpoint", "考试管理",
                "status", "基础框架已就绪"
        ));
    }
}
