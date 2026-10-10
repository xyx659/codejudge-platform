package com.codejudge.platform.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单语言判题处理器：封装一种编程语言「源码 → 编译 → 运行」的全部语言相关行为。
 *
 * <p>每种语言一个实现（Java / Python / Go），由 {@link LanguageHandlerRegistry} 按题目的
 * {@code language} 字段路由。判题编排（{@link DockerJudgeEngine} / {@link RunService}）只依赖
 * 本接口，不再感知任何具体语言。</p>
 *
 * <p>核心职责拆成两类：</p>
 * <ul>
 *   <li><b>命令</b>：{@link #compileCommand()}、{@link #runCommand(String)}、镜像 {@link #image()}、文件名。</li>
 *   <li><b>源码生成</b>：解析签名 {@link #parseSignature(String)}、按 {@code METHOD/DESIGN/STDIO}
 *       三种判题模式生成包装源码、注入数据结构定义文件 {@link #helperSources(MethodSignature)}。</li>
 * </ul>
 */
public interface LanguageHandler {

    /** 语言规范名（与 {@code Question.language} 存储值一致），如 {@code "Java"} / {@code "Python"} / {@code "Go"}。 */
    String language();

    /** 该语言判题用的 Docker 镜像名。 */
    String image();

    /** 学生源码文件名，如 {@code Solution.java} / {@code solution.py} / {@code solution.go}。 */
    String sourceFileName();

    /** 判题侧生成的包装文件名，如 {@code Main.java} / {@code main.py} / {@code main.go}。 */
    String wrapperFileName();

    /** 是否需要「先编译再运行」：编译型语言（Java/Go）为 {@code true}，解释型（Python）为 {@code false}。 */
    default boolean requiresCompile() {
        return true;
    }

    /** 编译命令（在容器工作目录内执行，一次编译，产物复用）。 */
    List<String> compileCommand();

    /** 运行命令；{@code inputFile} 为工作目录内已写入该用例输入的文件名。 */
    List<String> runCommand(String inputFile);

    /** 解析该语言自包含的方法签名。 */
    MethodSignature parseSignature(String signature);

    /**
     * 生成 METHOD 模式包装源码：读 stdin → 按签名解析入参 → 调用学生方法 → 打印结果。
     *
     * @param signature    方法签名
     * @param helperClasses 辅助类定义源码列表（用于识别 {@code Node} 形态等）；类定义本身由调用方
     *                      作为独立文件编译/运行
     */
    String generateMethodWrapper(MethodSignature signature, List<String> helperClasses);

    /** 生成 DESIGN 模式包装源码（多方法调用；第一个为构造器）。 */
    String generateDesignWrapper(List<MethodSignature> methods, String className);

    /** 生成 STDIO 模式包装源码（学生写完整程序，包装只负责转发）。 */
    String generateStdioWrapper();

    /** 按签名返回需要的数据结构定义文件（文件名 → 源码）。 */
    Map<String, String> helperSources(MethodSignature signature);

    /** 从学生源码提取类名/类型名（DESIGN 模式用），提取不到时回退默认值。 */
    default String extractClassName(String sourceCode) {
        return "Solution";
    }

    /**
     * 从「编译产物」中挑选运行所需文件：Java 取 {@code *.class}，Go 取可执行文件；解释型语言不调用本方法。
     * 默认原样返回。
     */
    default Map<String, byte[]> selectRunFiles(Map<String, byte[]> compiledFiles) {
        return compiledFiles;
    }

    /**
     * 运行阶段需要保留可执行位的文件名集合（如 Go 的编译产物 {@code solution}）。
     * 打包时对这些文件设 0755（含 other 执行位），否则容器内 {@code nobody} 无法执行。默认无。
     */
    default Set<String> executableNames() {
        return Set.of();
    }
}
