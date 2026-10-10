package com.codejudge.platform.service;

import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.ContainerRunRequest;
import com.codejudge.platform.dto.ContainerRunResult;
import com.codejudge.platform.dto.RunRequest;
import com.codejudge.platform.dto.RunResult;
import com.codejudge.platform.dto.RunTestCaseResult;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.repository.QuestionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 样例自测服务（轻量「测试」按钮后端）。
 *
 * <p>复用 {@link DockerJudgeEngine} 同一套 {@link LanguageHandlerRegistry} + {@link WorkspacePacker} +
 * {@link JudgeContainerClient} 编译运行链路，但<b>只跑样例用例</b>：不写提交记录、不触发 AI 评审，
 * 同步返回「编译错误 + 逐用例结果」，让学生看到的判卷结果与最终提交一致。</p>
 */
@Service
public class RunService {

    /** 容器内工作目录，与 {@link DockerJudgeEngine} 一致，落在 JudgeContainerClient 挂载的 tmpfs 内。 */
    private static final String WORK_DIR = "/tmp";

    /** 编译超时给独立固定值。 */
    private static final long COMPILE_TIMEOUT_MS = 20_000L;

    /** 编译与运行容器统一按 1 核限制。 */
    private static final double CPUS = 1.0;

    private final QuestionRepository questionRepository;
    private final SystemConfigService systemConfigService;
    private final LanguageHandlerRegistry languageHandlers;
    private final WorkspacePacker packer;
    private final JudgeContainerClient containerClient;

    public RunService(QuestionRepository questionRepository,
                      SystemConfigService systemConfigService,
                      LanguageHandlerRegistry languageHandlers,
                      WorkspacePacker packer,
                      JudgeContainerClient containerClient) {
        this.questionRepository = questionRepository;
        this.systemConfigService = systemConfigService;
        this.languageHandlers = languageHandlers;
        this.packer = packer;
        this.containerClient = containerClient;
    }

    /**
     * 编译并运行样例测试用例。
     *
     * @param request 自测请求（题目 ID + 源码 + 待运行用例）
     * @return 编译错误，或逐用例结果
     */
    public RunResult run(RunRequest request) {
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("题目不存在"));

        // 按题目语言路由到对应处理器（null/历史数据回退 Java）
        LanguageHandler handler = languageHandlers.get(question.getLanguage());

        String judgeMode = question.getJudgeMode() == null ? "METHOD" : question.getJudgeMode();
        boolean isDesign = "DESIGN".equals(judgeMode);
        boolean isStdio = "STDIO".equals(judgeMode);

        JudgeRuntimeConfig config = systemConfigService.getJudgeRuntimeConfig();

        // ① 组装源码：学生源码 + 判题侧包装 + 数据结构定义文件
        Map<String, String> helperSources;
        String wrapperSource;
        if (isStdio) {
            helperSources = new HashMap<>();
            wrapperSource = handler.generateStdioWrapper();
        } else if (isDesign) {
            List<String> methodDefs = question.getDesignMethods();
            if (methodDefs == null || methodDefs.isEmpty()) {
                return new RunResult("设计题缺少方法定义", List.of());
            }
            List<MethodSignature> signatures = new ArrayList<>();
            for (String def : methodDefs) {
                try {
                    signatures.add(handler.parseSignature(def));
                } catch (Exception e) {
                    return new RunResult("方法签名解析失败：" + def, List.of());
                }
            }
            helperSources = new HashMap<>();
            for (MethodSignature sig : signatures) {
                helperSources.putAll(handler.helperSources(sig));
            }
            String className = handler.extractClassName(request.sourceCode());
            wrapperSource = handler.generateDesignWrapper(signatures, className);
        } else {
            MethodSignature signature;
            try {
                signature = handler.parseSignature(question.getMethodSignature());
            } catch (Exception e) {
                return new RunResult("题目缺少合法方法签名", List.of());
            }
            helperSources = handler.helperSources(signature);
            List<String> helperClasses = new ArrayList<>(helperSources.values());
            wrapperSource = handler.generateMethodWrapper(signature, helperClasses);
        }

        Map<String, byte[]> sources = new HashMap<>();
        sources.put(handler.sourceFileName(), request.sourceCode().getBytes(StandardCharsets.UTF_8));
        sources.put(handler.wrapperFileName(), wrapperSource.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, String> e : helperSources.entrySet()) {
            sources.put(e.getKey(), e.getValue().getBytes(StandardCharsets.UTF_8));
        }

        // ② 编译（编译型语言）或直接运行（解释型语言）
        Map<String, byte[]> runBase;
        if (handler.requiresCompile()) {
            JudgeContainerClient.CompileResult cr = containerClient.compile(
                    new ContainerRunRequest(handler.compileCommand(), WORK_DIR,
                            packer.pack(sources), COMPILE_TIMEOUT_MS, config.memoryMb(), CPUS,
                            JudgeContainerClient.PIDS_LIMIT_COMPILE),
                    handler.image());
            if (cr.timedOut()) {
                return new RunResult("编译超时", List.of());
            }
            if (cr.exitCode() != 0) {
                return new RunResult("代码编译失败：" + truncate(cr.stderr()), List.of());
            }
            runBase = handler.selectRunFiles(packer.unpack(cr.outputTar()));
        } else {
            runBase = sources;
        }

        // ③ 逐用例运行
        List<QuestionTestCase> testCases = resolveTestCases(question, request.testCases());
        List<RunTestCaseResult> results = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            results.add(runCase(runBase, handler, testCases.get(i), i, config));
        }
        return new RunResult(null, results);
    }

    /** 待运行用例：请求里给了就用请求里的（考试页是随机抽取子集），否则回退题目全部用例。 */
    private List<QuestionTestCase> resolveTestCases(Question question, List<QuestionTestCase> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        return question.getTestCases() == null ? List.of() : question.getTestCases();
    }

    /** 运行单个用例：编译产物 + {@code inputN.txt} 打包，独立容器执行。 */
    private RunTestCaseResult runCase(Map<String, byte[]> runBase, LanguageHandler handler,
                                      QuestionTestCase tc, int index, JudgeRuntimeConfig config) {
        String inputFile = "input" + index + ".txt";
        Map<String, byte[]> runFiles = new HashMap<>(runBase);
        runFiles.put(inputFile, tc.getInput() == null ? new byte[0] : tc.getInput().getBytes(StandardCharsets.UTF_8));

        long start = System.currentTimeMillis();
        ContainerRunResult run = containerClient.run(new ContainerRunRequest(
                handler.runCommand(inputFile), WORK_DIR,
                packer.pack(runFiles, handler.executableNames()), config.timeoutMs(), config.memoryMb(), CPUS,
                JudgeContainerClient.PIDS_LIMIT_RUN),
                handler.image());
        long durationMs = System.currentTimeMillis() - start;

        String actual = run.stdout() == null ? "" : run.stdout().trim();
        String expected = tc.getExpected() == null ? "" : tc.getExpected().trim();
        String name = tc.getName() == null || tc.getName().isBlank() ? ("样例 " + (index + 1)) : tc.getName();

        boolean passed;
        String message;
        if (run.timedOut()) {
            passed = false;
            message = "超时（>" + config.timeoutMs() + "ms）";
        } else if (run.exitCode() != 0) {
            passed = false;
            message = "运行时错误：" + truncate(run.stderr());
        } else if (!JudgeOutputMatcher.outputMatches(actual, expected)) {
            passed = false;
            message = "输出不符";
        } else {
            passed = true;
            message = "通过";
        }
        return new RunTestCaseResult(name, passed, actual, expected, message, durationMs);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
