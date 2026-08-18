package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.QuestionRequest;
import com.codejudge.platform.dto.TeacherQuestionDetail;
import com.codejudge.platform.dto.TeacherQuestionSummary;
import com.codejudge.platform.service.TeacherQuestionService;
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
 * 教师端题库接口（对外地址都以 {@code /api/teacher/questions} 开头）。
 *
 * <p>教师端题库管理：列表（含未发布草稿）、详情、新增、修改、删除、发布/下架。</p>
 */
@RestController
@RequestMapping("/api/teacher/questions")
public class QuestionController {

    private final TeacherQuestionService questionService;

    public QuestionController(TeacherQuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * 题目列表（分页 + 关键字/难度/分类/标签筛选）。
     *
     * <p>请求示例：</p>
     * <pre>GET /api/teacher/questions?page=0&amp;size=10&amp;keyword=求和&amp;difficulty=简单</pre>
     */
    @GetMapping
    public ApiResponse<PageResult<TeacherQuestionSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String tag) {
        return ApiResponse.ok(questionService.list(page, size, keyword, difficulty, categoryId, tag));
    }

    /** 题目详情（含测试用例，供编辑回显） */
    @GetMapping("/{id}")
    public ApiResponse<TeacherQuestionDetail> detail(@PathVariable String id) {
        return ApiResponse.ok(questionService.detail(id));
    }

    /** 新增题目 */
    @PostMapping
    public ApiResponse<TeacherQuestionDetail> create(@RequestBody QuestionRequest request) {
        return ApiResponse.ok(questionService.create(request));
    }

    /** 修改题目 */
    @PutMapping("/{id}")
    public ApiResponse<TeacherQuestionDetail> update(@PathVariable String id,
                                                     @RequestBody QuestionRequest request) {
        return ApiResponse.ok(questionService.update(id, request));
    }

    /** 删除题目 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        questionService.delete(id);
        return ApiResponse.ok(null);
    }

    /** 发布 / 下架题目（published=true 发布，false 下架） */
    @PutMapping("/{id}/publish")
    public ApiResponse<TeacherQuestionDetail> publish(@PathVariable String id,
                                                      @RequestParam boolean published) {
        return ApiResponse.ok(questionService.publish(id, published));
    }
}
