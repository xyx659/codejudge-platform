package com.codejudge.platform.controller.teacher;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.dto.CategoryRequest;
import com.codejudge.platform.entity.Category;
import com.codejudge.platform.service.CategoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师端分类接口（对外地址都以 {@code /api/teacher/categories} 开头）。
 */
@RestController
@RequestMapping("/api/teacher/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 分类列表 */
    @GetMapping
    public ApiResponse<List<Category>> list() {
        return ApiResponse.ok(categoryService.list());
    }

    /** 新增分类 */
    @PostMapping
    public ApiResponse<Category> create(@RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    /** 修改分类 */
    @PutMapping("/{id}")
    public ApiResponse<Category> update(@PathVariable String id,
                                        @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    /** 删除分类 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ApiResponse.ok(null);
    }
}
