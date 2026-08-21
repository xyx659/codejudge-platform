package com.codejudge.platform.controller.student;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.ClientIpUtil;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExamSubmitRequest;
import com.codejudge.platform.dto.ExamSubmitResult;
import com.codejudge.platform.dto.QuestionDetail;
import com.codejudge.platform.dto.QuestionSummary;
import com.codejudge.platform.dto.StudentExamDetail;
import com.codejudge.platform.dto.StudentExamSummary;
import com.codejudge.platform.dto.StudentQuestionSubmission;
import com.codejudge.platform.dto.SubmissionRequest;
import com.codejudge.platform.dto.SubmissionResponse;
import com.codejudge.platform.dto.SubmissionResult;
import com.codejudge.platform.dto.SubmissionSummary;
import com.codejudge.platform.service.RateLimitService;
import com.codejudge.platform.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 学生端接口（对外地址都以 {@code /api/student} 开头）。
 *
 * <p>这里只做「接参数 → 调 Service → 包成统一响应」三步，具体逻辑都在 Service。</p>
 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final RateLimitService rateLimitService;

    public StudentController(StudentService studentService,
                             RateLimitService rateLimitService) {
        this.studentService = studentService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 学生端「我的考试」列表（学生看到的是试卷，而非题库题目）。
     *
     * <pre>GET /api/student/exams</pre>
     */
    @GetMapping("/exams")
    public ApiResponse<List<StudentExamSummary>> exams() {
        return ApiResponse.ok(studentService.listExams());
    }

    /**
     * 学生端考试详情：进试卷答题 / 交卷后回看。
     *
     * <pre>GET /api/student/exams/{id}</pre>
     */
    @GetMapping("/exams/{id}")
    public ApiResponse<StudentExamDetail> examDetail(@PathVariable String id) {
        return ApiResponse.ok(studentService.getExam(id));
    }

    /**
     * 整卷交卷接口：把试卷内各题答案一次性提交。
     *
     * <pre>
     * POST /api/student/exams/{id}/submit
     * { "answers": [ { "questionId": "...", "sourceCode": "..." } ] }
     * </pre>
     */
    @PostMapping("/exams/{id}/submit")
    public ApiResponse<ExamSubmitResult> submitExam(
            @PathVariable String id,
            @Valid @RequestBody ExamSubmitRequest request,
            HttpServletRequest servletRequest) {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        rateLimitService.checkSubmission(username, ClientIpUtil.resolve(servletRequest));
        return ApiResponse.ok(studentService.submitExam(id, request));
    }

    /**
     * 题目列表接口。
     *
     * <p>请求示例：</p>
     * <pre>GET /api/student/questions?page=0&amp;size=10&amp;difficulty=简单&amp;tag=数学</pre>
     *
     * @param page       页码，从 0 开始，不传默认 0
     * @param size       每页条数，不传默认 10
     * @param difficulty 难度筛选，可选
     * @param tag        标签筛选，可选
     * @return 分页的题目摘要列表
     */
    @GetMapping("/questions")
    public ApiResponse<PageResult<QuestionSummary>> questions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag) {
        return ApiResponse.ok(studentService.listQuestions(page, size, difficulty, tag));
    }

    /**
     * 题目详情接口（含测试用例说明）。
     *
     * <p>请求示例：</p>
     * <pre>GET /api/student/questions/65f1a2b3c4d5e6f7a8b9c0d1</pre>
     *
     * @param id 题目 ID（路径参数，对应 MongoDB 的 _id）
     * @return 题目完整详情
     */
    @GetMapping("/questions/{id}")
    public ApiResponse<QuestionDetail> questionDetail(@PathVariable String id) {
        return ApiResponse.ok(studentService.getQuestion(id));
    }

    /**
     * 当前学生对某道题的提交查询接口（提交后回看）。
     *
     * <p>请求示例：</p>
     * <pre>GET /api/student/questions/65f1a2b3c4d5e6f7a8b9c0d1/submission</pre>
     *
     * <p>未提交过时 {@code data} 为 {@code null}，前端据此判断是否允许提交。</p>
     *
     * @param id 题目 ID（路径参数）
     * @return 该题的提交视图（含源码与评测结果）；未提交时为 null
     */
    @GetMapping("/questions/{id}/submission")
    public ApiResponse<StudentQuestionSubmission> questionSubmission(@PathVariable String id) {
        return ApiResponse.ok(studentService.getQuestionSubmission(id));
    }

    /**
     * 代码提交接口（落库 → 触发评测）。
     *
     * <p>请求示例：</p>
     * <pre>
     * POST /api/student/submissions
     * { "questionId": "65f1a2b3c4d5e6f7a8b9c0d1", "sourceCode": "public class Solution { ... }" }
     * </pre>
     *
     * @param request 提交请求体（题目 ID + 源码）
     * @return 提交结果（提交 ID + 判卷状态）
     */
    @PostMapping("/submissions")
    public ApiResponse<SubmissionResponse> submit(
            @Valid @RequestBody SubmissionRequest request,
            HttpServletRequest servletRequest) {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        rateLimitService.checkSubmission(
                username,
                ClientIpUtil.resolve(servletRequest));
        return ApiResponse.ok(studentService.submit(request));
    }

    /**
     * 提交记录列表接口。
     *
     * <p>请求示例：</p>
     * <pre>GET /api/student/submissions?page=0&amp;size=10</pre>
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @return 分页的提交记录摘要（含题目标题）
     */
    @GetMapping("/submissions")
    public ApiResponse<PageResult<SubmissionSummary>> submissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(studentService.listSubmissions(page, size));
    }

    /**
     * 成绩与 AI 评审反馈查询接口。
     *
     * <p>请求示例：</p>
     * <pre>GET /api/student/submissions/1/result</pre>
     *
     * @param id 提交记录 ID（路径参数，对应 MySQL 的 submissions.id）
     * @return 提交结果（得分、测试用例结果、AI 评审）
     */
    @GetMapping("/submissions/{id}/result")
    public ApiResponse<SubmissionResult> submissionResult(@PathVariable Long id) {
        return ApiResponse.ok(studentService.getSubmissionResult(id));
    }
}
