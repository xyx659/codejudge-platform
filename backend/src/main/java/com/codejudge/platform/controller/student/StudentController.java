package com.codejudge.platform.controller.student;

import com.codejudge.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 学生端接口。
 *
 * <p>当前为骨架占位实现，仅返回状态提示，具体业务待补充。</p>
 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    /** 获取题目列表（占位） */
    @GetMapping("/questions")
    public ApiResponse<Map<String, Object>> questions() {
        return ApiResponse.ok(Map.of(
                "endpoint", "获取题目列表",
                "status", "基础框架已就绪"
        ));
    }

    /** 提交记录（占位） */
    @GetMapping("/submissions")
    public ApiResponse<Map<String, Object>> submissions() {
        return ApiResponse.ok(Map.of(
                "endpoint", "提交记录",
                "status", "基础框架已就绪"
        ));
    }
}
