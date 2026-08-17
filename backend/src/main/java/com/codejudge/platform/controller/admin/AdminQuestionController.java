package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.AuditOperation;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExternalQuestionCandidate;
import com.codejudge.platform.dto.ExternalQuestionImportRequest;
import com.codejudge.platform.dto.QuestionImportResult;
import com.codejudge.platform.dto.QuestionManageDetail;
import com.codejudge.platform.dto.QuestionManageSummary;
import com.codejudge.platform.dto.QuestionSaveRequest;
import com.codejudge.platform.dto.QuestionTestCaseRequest;
import com.codejudge.platform.service.ExternalQuestionService;
import com.codejudge.platform.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 管理端题库接口。
 */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;
    private final ExternalQuestionService externalQuestionService;

    public AdminQuestionController(
            QuestionService questionService,
            ExternalQuestionService externalQuestionService) {
        this.questionService = questionService;
        this.externalQuestionService = externalQuestionService;
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionManageSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean published) {
        return ApiResponse.ok(questionService.listQuestions(
                page, size, keyword, difficulty, tag, published));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionManageDetail> detail(@PathVariable String id) {
        return ApiResponse.ok(questionService.getQuestion(id));
    }

    @PostMapping
    @AuditOperation(
            module = "题库管理",
            operation = "CREATE_QUESTION",
            description = "新增题目")
    public ApiResponse<QuestionManageDetail> create(
            @Valid @RequestBody QuestionSaveRequest request) {
        return ApiResponse.ok(questionService.createManual(request));
    }

    @PutMapping("/{id}")
    @AuditOperation(
            module = "题库管理",
            operation = "UPDATE_QUESTION",
            description = "修改题目")
    public ApiResponse<QuestionManageDetail> update(
            @PathVariable String id,
            @Valid @RequestBody QuestionSaveRequest request) {
        return ApiResponse.ok(questionService.updateQuestion(id, request));
    }

    @PostMapping("/{id}/test-cases")
    @AuditOperation(
            module = "题库管理",
            operation = "ADD_TEST_CASE",
            description = "新增测试用例")
    public ApiResponse<QuestionManageDetail> addTestCase(
            @PathVariable String id,
            @Valid @RequestBody QuestionTestCaseRequest request) {
        return ApiResponse.ok(questionService.addTestCase(id, request));
    }

    @PutMapping("/{id}/test-cases/{index}")
    @AuditOperation(
            module = "题库管理",
            operation = "UPDATE_TEST_CASE",
            description = "修改测试用例")
    public ApiResponse<QuestionManageDetail> updateTestCase(
            @PathVariable String id,
            @PathVariable int index,
            @Valid @RequestBody QuestionTestCaseRequest request) {
        return ApiResponse.ok(questionService.updateTestCase(id, index, request));
    }

    @DeleteMapping("/{id}/test-cases/{index}")
    @AuditOperation(
            module = "题库管理",
            operation = "DELETE_TEST_CASE",
            description = "删除测试用例")
    public ApiResponse<QuestionManageDetail> deleteTestCase(
            @PathVariable String id,
            @PathVariable int index) {
        return ApiResponse.ok(questionService.deleteTestCase(id, index));
    }

    @PutMapping("/{id}/publish")
    @AuditOperation(
            module = "题库管理",
            operation = "PUBLISH_QUESTION",
            description = "发布或下架题目")
    public ApiResponse<QuestionManageDetail> publish(
            @PathVariable String id,
            @RequestParam boolean published) {
        return ApiResponse.ok(questionService.setPublished(id, published));
    }

    @DeleteMapping("/{id}")
    @AuditOperation(
            module = "题库管理",
            operation = "DELETE_QUESTION",
            description = "删除题目")
    public ApiResponse<Void> delete(@PathVariable String id) {
        questionService.deleteQuestion(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/external/search")
    public ApiResponse<PageResult<ExternalQuestionCandidate>> searchExternal(
            @RequestParam String platform,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(externalQuestionService.search(
                platform, keyword, difficulty, page, size));
    }

    @PostMapping("/external/import")
    @AuditOperation(
            module = "题库管理",
            operation = "IMPORT_EXTERNAL_QUESTION",
            description = "从外部平台导入题目")
    public ApiResponse<QuestionManageDetail> importExternal(
            @Valid @RequestBody ExternalQuestionImportRequest request) {
        return ApiResponse.ok(externalQuestionService.importQuestion(
                request.platform(), request.sourceId()));
    }

    @PostMapping(value = "/import-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditOperation(
            module = "题库管理",
            operation = "IMPORT_QUESTION_TEMPLATE",
            description = "通过 JSON 模板导入题目")
    public ApiResponse<QuestionImportResult> importTemplate(
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(questionService.importTemplate(file));
    }

    @GetMapping(value = "/import-template", produces = "application/json;charset=UTF-8")
    public ResponseEntity<byte[]> downloadTemplate() {
        String content = """
                {
                  "questions": [
                    {
                      "title": "两数之和",
                      "description": "实现 sum(int a, int b)，返回两数之和",
                      "methodName": "sum",
                      "language": "Java",
                      "difficulty": "简单",
                      "tags": ["数学", "基础"],
                      "published": false,
                      "testCases": [
                        {
                          "name": "样例1",
                          "input": "1 2",
                          "expected": "3"
                        }
                      ]
                    }
                  ]
                }
                """;
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"question-import-template.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
