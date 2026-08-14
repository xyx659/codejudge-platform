package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库健康检查接口。
 *
 * <p>用于验证 MySQL 与 MongoDB 连接是否正常，并返回各库初始化数据量。</p>
 */
@RestController
@RequestMapping("/api/admin/db")
public class DatabaseCheckController {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionDetailRepository submissionDetailRepository;

    public DatabaseCheckController(UserRepository userRepository,
                                   QuestionRepository questionRepository,
                                   SubmissionRepository submissionRepository,
                                   SubmissionDetailRepository submissionDetailRepository) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
    }

    /** 检查两个数据库的连接状态与数据量 */
    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> check() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("mysql", Map.of(
                "status", "ok",
                "users", userRepository.count(),
                "submissions", submissionRepository.count()
        ));
        data.put("mongodb", Map.of(
                "status", "ok",
                "questions", questionRepository.count(),
                "submission_details", submissionDetailRepository.count()
        ));
        return ApiResponse.ok(data);
    }
}
