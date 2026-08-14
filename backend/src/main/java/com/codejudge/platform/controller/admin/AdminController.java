package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端接口。
 *
 * <p>当前为骨架占位实现，仅返回状态提示，具体业务待补充。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /** 用户管理（占位） */
    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> users() {
        return ApiResponse.ok(Map.of(
                "endpoint", "用户管理",
                "status", "基础框架已就绪"
        ));
    }

    /** 考试监控（占位） */
    @GetMapping("/monitor")
    public ApiResponse<Map<String, Object>> monitor() {
        return ApiResponse.ok(Map.of(
                "endpoint", "考试监控",
                "status", "基础框架已就绪"
        ));
    }
}
