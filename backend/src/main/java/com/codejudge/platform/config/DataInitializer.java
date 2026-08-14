package com.codejudge.platform.config;

import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.entity.TestCaseResult;
import com.codejudge.platform.entity.User;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时初始化演示数据。
 *
 * <p>作为 {@link CommandLineRunner} 在应用启动完成后执行，
 * 仅在对应集合为空时才写入，保证重复启动幂等、不覆盖已有数据。</p>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionDetailRepository submissionDetailRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           QuestionRepository questionRepository,
                           SubmissionRepository submissionRepository,
                           SubmissionDetailRepository submissionDetailRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 1. 初始化三角色账号（密码 BCrypt 加密存储）
        if (userRepository.count() == 0) {
            User admin = new User("admin", "管理员", passwordEncoder.encode("admin123"), "ADMIN");
            User teacher = new User("teacher", "王老师", passwordEncoder.encode("teacher123"), "TEACHER");
            User zhang = new User("zhang", "张小明", passwordEncoder.encode("123456"), "STUDENT");
            zhang.setStudentNo("20260001");
            userRepository.saveAll(List.of(admin, teacher, zhang));
        }

        // 2. 初始化一道示例题目（含两个测试用例）
        Question question = null;
        if (questionRepository.count() == 0) {
            question = new Question("两数之和", "实现 sum(int a, int b)，返回两数之和", "sum");
            question.setLanguage("Java");
            question.setDifficulty("简单");
            question.setTags(List.of("数学", "基础"));
            question.setTestCases(List.of(
                    new QuestionTestCase("基本用例 1+2", "1 2", "3"),
                    new QuestionTestCase("负数 -5+5", "-5 5", "0")
            ));
            question.setPublished(true);
            question = questionRepository.save(question);
        } else {
            // 已有题目时取第一条作为演示关联对象
            question = questionRepository.findAll().get(0);
        }
        final Question finalQuestion = question;

        // 3. 初始化一条提交元数据（MySQL，判卷摘要）
        if (submissionRepository.count() == 0 && finalQuestion != null) {
            userRepository.findByUsername("zhang").ifPresent(zhang -> {
                Submission submission = new Submission(finalQuestion.getId(), zhang.getId());
                submission.setJudgeStatus("RUN_COMPLETED");
                submission.setScore(91);
                submissionRepository.save(submission);
            });
        }

        // 4. 初始化一条提交明细（MongoDB，含源码、测试结果与 AI 评审）
        if (submissionDetailRepository.count() == 0 && finalQuestion != null) {
            userRepository.findByUsername("zhang").ifPresent(zhang -> {
                SubmissionDetail detail = new SubmissionDetail();
                detail.setStudentId(zhang.getId());
                detail.setQuestionId(finalQuestion.getId());
                detail.setSourceCode("public class Solution {\n"
                        + "    public static int sum(int a, int b) {\n"
                        + "        return a + b;\n"
                        + "    }\n"
                        + "}\n");
                detail.setJudgeStatus("RUN_COMPLETED");
                detail.setScore(91);
                detail.setTestResults(List.of(
                        new TestCaseResult("基本用例 1+2", true, "3", "通过", 1L),
                        new TestCaseResult("负数 -5+5", true, "0", "通过", 1L)
                ));
                detail.setAiReview(new AiReview(91, 100, 70, List.of(
                        "黑盒测试：通过 2/2 个用例。",
                        "白盒分析：代码简洁，建议补充注释。"
                )));
                submissionDetailRepository.save(detail);
            });
        }
    }
}
