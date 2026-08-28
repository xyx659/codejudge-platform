package com.codejudge.platform.service;

import com.codejudge.platform.dto.ContainerRunRequest;
import com.codejudge.platform.dto.ContainerRunResult;
import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.entity.TestCaseResult;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自研容器判题引擎（Step 5）：拉数据 → 生成 {@code Main} → 容器内 {@code javac} 编译
 * → 逐用例独立容器运行 → 比对 → 按通过率算分。
 *
 * <p>执行模型：一题只编译一次，编译产物（{@code .class}）通过 {@code docker cp} 回拷给宿主，
 * 逐用例时再打包 {@code .class} + 输入文件、每个用例用一个独立容器运行，保证用例间隔离与精确超时。</p>
 *
 * <p>状态映射：编译失败 → {@code COMPILE_ERROR}，编译超时 → {@code TIMEOUT}（score=0）；
 * 其余 → {@code RUN_COMPLETED}，用例级失败（超时 / 运行时错误 / 输出不符）记在单个 {@link TestCaseResult} 中。</p>
 */
@Component
public class DockerJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerJudgeEngine.class);

    /** 容器内工作目录：必须落在 JudgeContainerClient 挂载的 tmpfs（{@code /tmp}）内，{@code nobody} 可写。 */
    private static final String WORK_DIR = "/tmp";

    /** 编译超时给独立固定值，通常略宽于单用例超时。 */
    private static final long COMPILE_TIMEOUT_MS = 20_000L;

    /** 编译与运行容器统一按 1 核限制。 */
    private static final double CPUS = 1.0;

    private final SubmissionRepository submissionRepository;
    private final SubmissionDetailRepository submissionDetailRepository;
    private final QuestionRepository questionRepository;
    private final SystemConfigService systemConfigService;
    private final CodeRunner codeRunner;
    private final WorkspacePacker packer;
    private final JudgeContainerClient containerClient;
    private final AiReviewService aiReviewService;

    public DockerJudgeEngine(SubmissionRepository submissionRepository,
                             SubmissionDetailRepository submissionDetailRepository,
                             QuestionRepository questionRepository,
                             SystemConfigService systemConfigService,
                             CodeRunner codeRunner,
                             WorkspacePacker packer,
                             JudgeContainerClient containerClient,
                             AiReviewService aiReviewService) {
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.questionRepository = questionRepository;
        this.systemConfigService = systemConfigService;
        this.codeRunner = codeRunner;
        this.packer = packer;
        this.containerClient = containerClient;
        this.aiReviewService = aiReviewService;
    }

    @Override
    public void judge(Long submissionId) {
        try {
            evaluate(submissionId);
        } catch (Exception e) {
            // 异步线程内任何未预期异常都吞掉并落库，避免提交状态卡在 PENDING。
            log.error("评测流程异常：submissionId={}", submissionId, e);
            markCompileError(submissionId, "评测流程异常：" + e.getMessage());
        }
    }

    /** 评测主流程：拉数据 → 编译 → 逐用例运行 → 算分 → 回写。 */
    private void evaluate(Long submissionId) {
        // ① 拉数据
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.error("提交记录不存在：submissionId={}", submissionId);
            return;
        }
        SubmissionDetail detail = submissionDetailRepository
                .findBySubmissionIdAndStudentId(submissionId, submission.getStudentId())
                .orElse(null);
        if (detail == null) {
            log.error("提交明细不存在：submissionId={}", submissionId);
            return;
        }
        Question question = questionRepository.findById(submission.getQuestionId()).orElse(null);
        if (question == null) {
            finish(submission, detail, "COMPILE_ERROR", 0,
                    List.of(new TestCaseResult("评测", false, "", "题目不存在", 0)));
            return;
        }

        CodeRunner.MethodSignature signature;
        try {
            signature = codeRunner.parseSignature(question.getMethodSignature());
        } catch (Exception e) {
            finish(submission, detail, "COMPILE_ERROR", 0,
                    List.of(new TestCaseResult("评测", false, "", "题目缺少合法方法签名", 0)));
            return;
        }

        JudgeRuntimeConfig config = systemConfigService.getJudgeRuntimeConfig();

        // ② 编译：Solution.java（学生源码） + Main.java（判题侧包装） + 数据结构定义文件
        String mainSource = codeRunner.generateMain(signature);
        Map<String, byte[]> sources = new HashMap<>();
        sources.put("Solution.java", detail.getSourceCode().getBytes(StandardCharsets.UTF_8));
        sources.put("Main.java", mainSource.getBytes(StandardCharsets.UTF_8));
        // 根据签名自动注入 ListNode/TreeNode/Node 独立源码，使 Solution.java 能引用这些类
        for (Map.Entry<String, String> e : codeRunner.requiredHelperSources(signature).entrySet()) {
            sources.put(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }

        JudgeContainerClient.CompileResult cr = containerClient.compile(
                new ContainerRunRequest(codeRunner.compileCommand(), WORK_DIR,
                        packer.pack(sources), COMPILE_TIMEOUT_MS, config.memoryMb(), CPUS));

        if (cr.timedOut()) {
            finish(submission, detail, "TIMEOUT", 0,
                    List.of(new TestCaseResult("编译", false, "", "编译超时", COMPILE_TIMEOUT_MS)));
            return;
        }
        if (cr.exitCode() != 0) {
            finish(submission, detail, "COMPILE_ERROR", 0,
                    List.of(new TestCaseResult("编译", false, "", truncate(cr.stderr()), 0)));
            return;
        }

        // ③ 逐用例运行：复用编译产物 .class，每个用例一个独立容器
        Map<String, byte[]> classFiles = packer.unpack(cr.outputTar());
        List<QuestionTestCase> testCases = question.getTestCases();
        List<TestCaseResult> results = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            results.add(runCase(classFiles, testCases.get(i), i, config));
        }

        // ④ 算分：通过用例占比 × 100，四舍五入
        int passCount = 0;
        for (TestCaseResult r : results) {
            if (r.isPassed()) {
                passCount++;
            }
        }
        int score = testCases.isEmpty()
                ? 0
                : (int) Math.round((double) passCount * 100 / testCases.size());

        // 结果回写（Step 6 的落库动作，此处一并写出使引擎可直接替换 Stub）
        finish(submission, detail, "RUN_COMPLETED", score, results);

        // Step 7：白盒 AI 评审（仅编译通过后触发）
        triggerAiReview(submissionId, question, detail, score, results);
    }

    /** 运行单个用例：{@code .class} + {@code inputN.txt} 打包，独立容器执行 {@code java Main}。 */
    private TestCaseResult runCase(Map<String, byte[]> classFiles,
                                   QuestionTestCase tc, int index, JudgeRuntimeConfig config) {
        String inputFile = "input" + index + ".txt";
        Map<String, byte[]> runFiles = new HashMap<>();
        for (Map.Entry<String, byte[]> e : classFiles.entrySet()) {
            if (e.getKey().endsWith(".class")) {
                runFiles.put(e.getKey(), e.getValue());
            }
        }
        runFiles.put(inputFile, tc.getInput().getBytes(StandardCharsets.UTF_8));

        long start = System.currentTimeMillis();
        ContainerRunResult run = containerClient.run(new ContainerRunRequest(
                codeRunner.runCommand(inputFile), WORK_DIR,
                packer.pack(runFiles), config.timeoutMs(), config.memoryMb(), CPUS));
        long durationMs = System.currentTimeMillis() - start;

        String actual = run.stdout() == null ? "" : run.stdout().trim();
        String expected = tc.getExpected() == null ? "" : tc.getExpected().trim();

        boolean passed;
        String message;
        if (run.timedOut()) {
            passed = false;
            message = "超时（>" + config.timeoutMs() + "ms）";
        } else if (run.exitCode() != 0) {
            passed = false;
            message = "运行时错误：" + truncate(run.stderr());
        } else if (!actual.equals(expected)) {
            passed = false;
            message = "输出不符：期望=" + expected + "，实际=" + actual;
        } else {
            passed = true;
            message = "通过";
        }
        return new TestCaseResult(tc.getName(), passed, actual, message, durationMs);
    }

    /** Step 7：白盒 AI 评审。仅编译通过后触发；未配置 Key、调用或解析失败时 aiReview 保持为 null。 */
    private void triggerAiReview(Long submissionId, Question question, SubmissionDetail detail,
                                 int passRate, List<TestCaseResult> results) {
        try {
            AiReview aiReview = aiReviewService.review(
                    question.getTitle(), question.getDescription(), question.getMethodSignature(),
                    detail.getSourceCode(), passRate, results);
            if (aiReview != null) {
                detail.setAiReview(aiReview);
                saveDetail(detail);
            }
        } catch (Exception e) {
            // 兜底：AI 评审异常绝不影响黑盒判题结果
            log.warn("AI 评审异常，已跳过：submissionId={}", submissionId, e);
        }
    }

    /** 回写评测结果：MySQL submissions 摘要 + MongoDB submission_details 明细；任一侧失败都记 error 告警。 */
    private void finish(Submission submission, SubmissionDetail detail,
                        String status, int score, List<TestCaseResult> results) {
        submission.setJudgeStatus(status);
        submission.setScore(score);
        saveSubmission(submission);

        detail.setJudgeStatus(status);
        detail.setScore(score);
        detail.setTestResults(results);
        saveDetail(detail);

        log.info("评测完成：submissionId={}, status={}, score={}, 用例数={}",
                submission.getId(), status, score, results.size());
    }

    /** 未预期异常兜底：把 MySQL + MongoDB 两库都标为 COMPILE_ERROR，避免状态卡在 PENDING。 */
    private void markCompileError(Long submissionId, String message) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission != null) {
            submission.setJudgeStatus("COMPILE_ERROR");
            submission.setScore(0);
            saveSubmission(submission);
        }

        if (submission != null) {
            submissionDetailRepository
                    .findBySubmissionIdAndStudentId(submissionId, submission.getStudentId())
                    .ifPresent(detail -> {
                        detail.setJudgeStatus("COMPILE_ERROR");
                        detail.setScore(0);
                        saveDetail(detail);
                    });
        }

        log.error("评测失败：submissionId={}, {}", submissionId, message);
    }

    /** 写 MySQL submissions；失败只记 error，不向上抛，避免评测线程中断。 */
    private void saveSubmission(Submission submission) {
        try {
            submissionRepository.save(submission);
        } catch (Exception e) {
            log.error("回写 MySQL submissions 失败：submissionId={}", submission.getId(), e);
        }
    }

    /** 写 MongoDB submission_details；失败只记 error。 */
    private void saveDetail(SubmissionDetail detail) {
        try {
            submissionDetailRepository.save(detail);
        } catch (Exception e) {
            log.error("回写 MongoDB submission_details 失败：submissionId={}",
                    detail.getSubmissionId(), e);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}