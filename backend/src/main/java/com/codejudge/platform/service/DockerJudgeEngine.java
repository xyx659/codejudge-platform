package com.codejudge.platform.service;

import com.codejudge.platform.dto.ContainerRunRequest;
import com.codejudge.platform.dto.ContainerRunResult;
import com.codejudge.platform.dto.MethodSignature;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自研容器判题引擎（Step 5）：拉数据 → 按题目语言路由到对应 {@link LanguageHandler}
 * → 生成包装文件 → 容器内编译 → 逐用例独立容器运行 → 比对 → 按通过率算分。
 *
 * <p>执行模型：一题只编译一次，编译产物（{@code .class} / C/C++ 的 {@code main} 二进制）
 * 通过 {@code docker cp} 回拷给宿主，逐用例时再打包编译产物 + 输入文件、每个用例用一个
 * 独立容器运行，保证用例间隔离与精确超时。</p>
 *
 * <p>语言相关的一切（源码文件名、包装文件名、编译命令、运行命令、镜像）都委托给
 * {@code LanguageHandlerRegistry} 按 {@code question.language} 取到的处理器，引擎本身不再
 * 认识具体语言。</p>
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
    private final LanguageHandlerRegistry languageHandlerRegistry;
    private final WorkspacePacker packer;
    private final JudgeContainerClient containerClient;
    private final AiReviewService aiReviewService;

    public DockerJudgeEngine(SubmissionRepository submissionRepository,
                             SubmissionDetailRepository submissionDetailRepository,
                             QuestionRepository questionRepository,
                             SystemConfigService systemConfigService,
                             CodeRunner codeRunner,
                             LanguageHandlerRegistry languageHandlerRegistry,
                             WorkspacePacker packer,
                             JudgeContainerClient containerClient,
                             AiReviewService aiReviewService) {
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.questionRepository = questionRepository;
        this.systemConfigService = systemConfigService;
        this.codeRunner = codeRunner;
        this.languageHandlerRegistry = languageHandlerRegistry;
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

    /** 评测主流程：拉数据 → 路由处理器 → 生成包装 → 编译 → 逐用例运行 → 算分 → 回写。 */
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

        // 按题目语言路由到对应处理器（旧数据无 language 时按 Java 兼容）
        String language = question.getLanguage() == null ? "Java" : question.getLanguage();
        LanguageHandler handler;
        try {
            handler = languageHandlerRegistry.get(language);
        } catch (Exception e) {
            finish(submission, detail, "COMPILE_ERROR", 0,
                    List.of(new TestCaseResult("评测", false, "", e.getMessage(), 0)));
            return;
        }

        String judgeMode = question.getJudgeMode() == null ? "METHOD" : question.getJudgeMode();
        boolean isDesign = "DESIGN".equals(judgeMode);
        boolean isStdio = "STDIO".equals(judgeMode);

        JudgeRuntimeConfig config = systemConfigService.getJudgeRuntimeConfig();

        // ② 生成包装：学生源码 + 判题侧包装 + 数据结构辅助文件
        Map<String, String> helperSources;
        String mainSource;
        Set<String> sourceNames = new HashSet<>();

        if (isStdio) {
            // STDIO 模式：学生写完整程序，包装只是调用学生入口
            helperSources = new HashMap<>();
            mainSource = handler.generateStdioWrapper();
        } else if (isDesign) {
            // 设计题：解析多个方法签名
            List<String> methodDefs = question.getDesignMethods();
            if (methodDefs == null || methodDefs.isEmpty()) {
                finish(submission, detail, "COMPILE_ERROR", 0,
                        List.of(new TestCaseResult("评测", false, "", "设计题缺少方法定义", 0)));
                return;
            }
            List<MethodSignature> signatures = new ArrayList<>();
            for (String def : methodDefs) {
                try {
                    signatures.add(toDto(codeRunner.parseSignature(def)));
                } catch (Exception e) {
                    finish(submission, detail, "COMPILE_ERROR", 0,
                            List.of(new TestCaseResult("评测", false, "", "方法签名解析失败：" + def, 0)));
                    return;
                }
            }
            // 收集所有方法涉及的辅助类
            helperSources = new HashMap<>();
            for (MethodSignature sig : signatures) {
                helperSources.putAll(handler.helperSources(sig));
            }
            // 设计题类名：Java/C++ 从学生源码提取 class 名；C 用构造器方法名（struct 命名约定）
            String className = "C".equals(handler.language())
                    ? null
                    : codeRunner.extractClassName(detail.getSourceCode());
            mainSource = handler.generateDesignWrapper(signatures, className);
        } else {
            // 普通方法题
            MethodSignature signature;
            try {
                signature = toDto(codeRunner.parseSignature(question.getMethodSignature()));
            } catch (Exception e) {
                finish(submission, detail, "COMPILE_ERROR", 0,
                        List.of(new TestCaseResult("评测", false, "", "题目缺少合法方法签名", 0)));
                return;
            }
            helperSources = handler.helperSources(signature);
            List<String> helperClasses = new ArrayList<>(helperSources.values());
            mainSource = handler.generateMethodWrapper(signature, helperClasses);
        }

        sourceNames.add(handler.sourceFileName());
        sourceNames.add(handler.wrapperFileName());
        sourceNames.addAll(helperSources.keySet());

        Map<String, byte[]> sources = new HashMap<>();
        sources.put(handler.sourceFileName(), detail.getSourceCode().getBytes(StandardCharsets.UTF_8));
        sources.put(handler.wrapperFileName(), mainSource.getBytes(StandardCharsets.UTF_8));
        // 注入 ListNode/TreeNode/Node 等独立源码/头文件，使学生代码能引用这些类型
        for (Map.Entry<String, String> e : helperSources.entrySet()) {
            sources.put(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }

        // 编译：解释型语言（compileCommand 为空）跳过编译，直接以源文件为运行产物
        Map<String, byte[]> artifacts;
        List<String> compileCommand = handler.compileCommand();
        if (compileCommand == null || compileCommand.isEmpty()) {
            artifacts = new HashMap<>(sources);
        } else {
            JudgeContainerClient.CompileResult cr = containerClient.compile(
                    new ContainerRunRequest(handler.image(), compileCommand, WORK_DIR,
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
            artifacts = packer.unpack(cr.outputTar());
        }

        // ③ 逐用例运行：复用编译产物，每个用例一个独立容器
        List<QuestionTestCase> testCases = question.getTestCases();
        List<TestCaseResult> results = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            results.add(runCase(artifacts, sourceNames, testCases.get(i), i, config, handler));
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

    /**
     * 运行单个用例：编译产物 + {@code inputN.txt} 打包，独立容器执行运行命令。
     *
     * <p>{@code artifacts} 为编译容器回拷的全量工作目录（含源码 + 产物），这里只保留编译产物
     * （排除源码/头文件），避免把学生源码再拷进运行容器浪费空间、也避免个别语言产物被误删。</p>
     */
    private TestCaseResult runCase(Map<String, byte[]> artifacts, Set<String> sourceNames,
                                   QuestionTestCase tc, int index, JudgeRuntimeConfig config,
                                   LanguageHandler handler) {
        String inputFile = "input" + index + ".txt";
        Map<String, byte[]> runFiles = new HashMap<>();
        for (Map.Entry<String, byte[]> e : artifacts.entrySet()) {
            if (!sourceNames.contains(e.getKey())) {
                runFiles.put(e.getKey(), e.getValue());
            }
        }
        runFiles.put(inputFile, tc.getInput().getBytes(StandardCharsets.UTF_8));

        // 编译产物（C/C++ 的 main 二进制等）需带可执行位，否则容器内 ./main 报 Permission denied
        Set<String> executables = new HashSet<>(runFiles.keySet());
        executables.remove(inputFile);

        long start = System.currentTimeMillis();
        ContainerRunResult run = containerClient.run(new ContainerRunRequest(
                handler.image(), handler.runCommand(inputFile), WORK_DIR,
                packer.pack(runFiles, executables), config.timeoutMs(), config.memoryMb(), CPUS));
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
        } else if (!outputMatches(actual, expected)) {
            passed = false;
            message = "输出不符：期望=" + expected + "，实际=" + actual;
        } else {
            passed = true;
            message = "通过";
        }
        return new TestCaseResult(tc.getName(), passed, actual, message, durationMs);
    }

    /**
     * 智能输出比对：先精确匹配，失败后尝试浮点模糊比对和集合无序比对。
     */
    private boolean outputMatches(String actual, String expected) {
        // 1. 精确匹配（最快路径）
        if (actual.equals(expected)) {
            return true;
        }
        // 2. 浮点模糊比对：两端都是单个数值时，允许 1e-5 误差
        if (isNumeric(actual) && isNumeric(expected)) {
            try {
                double a = Double.parseDouble(actual);
                double e = Double.parseDouble(expected);
                if (Math.abs(a - e) < 1e-5) {
                    return true;
                }
                // 处理相对误差（数值很大时）
                if (e != 0 && Math.abs((a - e) / e) < 1e-5) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        // 3. 集合/数组无序比对：两端都是 [...] 格式时，排序后比较
        if (actual.startsWith("[") && expected.startsWith("[")) {
            return arrayMatches(actual, expected);
        }
        return false;
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 数组/集合无序比对：按逗号分割、排序后逐元素比较。
     * 处理嵌套数组时保持子数组原样，只对顶层排序。
     */
    private boolean arrayMatches(String actual, String expected) {
        try {
            String[] aTokens = splitTopLevel(actual.substring(1, actual.length() - 1));
            String[] eTokens = splitTopLevel(expected.substring(1, expected.length() - 1));
            if (aTokens.length != eTokens.length) return false;
            // 先尝试有序比较（多数情况有序即可）
            if (Arrays.equals(aTokens, eTokens)) return true;
            // 无序比较：排序后比较
            String[] aSorted = aTokens.clone();
            String[] eSorted = eTokens.clone();
            Arrays.sort(aSorted);
            Arrays.sort(eSorted);
            return Arrays.equals(aSorted, eSorted);
        } catch (Exception e) {
            return false;
        }
    }

    /** 按顶层逗号拆分，忽略嵌套括号内的逗号。 */
    private String[] splitTopLevel(String s) {
        java.util.List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '[' || c == '(' || c == '{') depth++;
                else if (c == ']' || c == ')' || c == '}') depth--;
            }
            if (!inStr && depth == 0 && c == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    /** {@code CodeRunner.MethodSignature} → {@code dto.MethodSignature}（字段一致，仅类型转换）。 */
    private static MethodSignature toDto(CodeRunner.MethodSignature s) {
        return new MethodSignature(s.returnType(), s.methodName(), s.paramTypes());
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
                // 综合分回写：更新 MongoDB 明细 + MySQL 摘要
                detail.setScore(aiReview.getScore());
                saveDetail(detail);
                submissionRepository.findById(submissionId).ifPresent(sub -> {
                    sub.setScore(aiReview.getScore());
                    saveSubmission(sub);
                });
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
