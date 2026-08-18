package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.dto.MonitorSummary;
import com.codejudge.platform.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端监考接口（对外地址都以 {@code /api/teacher/monitor} 开头）。
 *
 * <p>前端监考页每 5 秒轮询本接口，拉取一场考试的整体进度与预警。</p>
 */
@RestController
@RequestMapping("/api/teacher/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /** 一场考试的监考总览（进度 + 学生状态 + 预警） */
    @GetMapping("/{examId}")
    public ApiResponse<MonitorSummary> summary(@PathVariable String examId) {
        return ApiResponse.ok(monitorService.summary(examId));
    }
}
