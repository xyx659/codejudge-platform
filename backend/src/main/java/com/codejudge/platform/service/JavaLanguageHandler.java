package com.codejudge.platform.service;

import com.codejudge.platform.dto.MethodSignature;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Java 判题处理器：把已有 {@link CodeRunner}（Java 包装器）适配到 {@link LanguageHandler} 接口。
 *
 * <p>最小改动方案：{@link CodeRunner} 保持不动，本类只做薄适配 + 方法签名类型转换
 * （{@code CodeRunner.MethodSignature} ↔ {@code dto.MethodSignature}，两者字段完全一致）。</p>
 */
@Service
public class JavaLanguageHandler implements LanguageHandler {

    private final CodeRunner codeRunner;

    public JavaLanguageHandler(CodeRunner codeRunner) {
        this.codeRunner = codeRunner;
    }

    @Override
    public String language() {
        return "Java";
    }

    @Override
    public String image() {
        return "eclipse-temurin:17";
    }

    @Override
    public String sourceFileName() {
        return "Solution.java";
    }

    @Override
    public String wrapperFileName() {
        return "Main.java";
    }

    @Override
    public List<String> compileCommand() {
        return codeRunner.compileCommand();
    }

    @Override
    public List<String> runCommand(String inputFile) {
        return codeRunner.runCommand(inputFile);
    }

    @Override
    public String generateMethodWrapper(MethodSignature signature, List<String> helperClasses) {
        return codeRunner.generateMain(toCodeRunner(signature), helperClasses);
    }

    @Override
    public String generateDesignWrapper(List<MethodSignature> methods, String className) {
        List<CodeRunner.MethodSignature> converted = methods.stream()
                .map(this::toCodeRunner)
                .toList();
        return codeRunner.generateDesignMain(converted, className);
    }

    @Override
    public String generateStdioWrapper() {
        return codeRunner.generateStdioMain();
    }

    @Override
    public Map<String, String> helperSources(MethodSignature signature) {
        return codeRunner.requiredHelperSources(toCodeRunner(signature));
    }

    /** {@code dto.MethodSignature} → {@code CodeRunner.MethodSignature}。 */
    private CodeRunner.MethodSignature toCodeRunner(MethodSignature s) {
        return new CodeRunner.MethodSignature(s.returnType(), s.methodName(), s.paramTypes());
    }
}
