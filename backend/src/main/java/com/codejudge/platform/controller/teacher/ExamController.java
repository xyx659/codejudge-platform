package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExamRequest;
import com.codejudge.platform.dto.ExamSummary;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.service.ExamService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端考试接口（对外地址都以 {@code /api/teacher/exams} 开头）。
 *
 * <p>考试管理：列表、详情、新建（组卷）、修改、删除、发布、关闭。</p>
 */
@RestController
@RequestMapping("/api/teacher/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /** 考试列表（分页 + 状态/分类筛选） */
    @GetMapping
    public ApiResponse<PageResult<ExamSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryId) {
        return ApiResponse.ok(examService.list(page, size, status, categoryId));
    }

    /** 考试详情（含组卷题目，供编辑回显） */
    @GetMapping("/{id}")
    public ApiResponse<Exam> detail(@PathVariable String id) {
        return ApiResponse.ok(examService.detail(id));
    }

    /** 新建考试（草稿） */
    @PostMapping
    public ApiResponse<Exam> create(@RequestBody ExamRequest request) {
        return ApiResponse.ok(examService.create(request));
    }

    /** 修改考试 */
    @PutMapping("/{id}")
    public ApiResponse<Exam> update(@PathVariable String id,
                                    @RequestBody ExamRequest request) {
        return ApiResponse.ok(examService.update(id, request));
    }

    /** 删除考试 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        examService.delete(id);
        return ApiResponse.ok(null);
    }

    /** 发布考试 */
    @PutMapping("/{id}/publish")
    public ApiResponse<Exam> publish(@PathVariable String id) {
        return ApiResponse.ok(examService.publish(id));
    }

    /** 关闭考试 */
    @PutMapping("/{id}/close")
    public ApiResponse<Exam> close(@PathVariable String id) {
        return ApiResponse.ok(examService.close(id));
    }
}
