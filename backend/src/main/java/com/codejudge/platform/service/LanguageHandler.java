package com.codejudge.platform.service;

import com.codejudge.platform.dto.MethodSignature;

import java.util.List;
import java.util.Map;

/**
 * 单语言判题处理器：把「语言相关的一切」从 {@link DockerJudgeEngine} 抽离出来。
 *
 * <p>每种语言（Java / Python / Go / C / C++）实现一个，由 {@link LanguageHandlerRegistry}
 * 按题目 {@code question.language} 路由，判题引擎不再认识具体语言。</p>
 *
 * <p><b>统一 IO 协议</b>：包装文件读 stdin、按 LeetCode 格式（{@code nums = [2,7,11,15], target = 9}）
 * 解析参数、调用学生代码、把结果按 LeetCode 序列化格式打印到 stdout（数组 {@code [1,2,3]}、
 * 树 {@code [1,2,3,null,4]}）。判题侧的输入/输出比对逻辑因此完全复用，无需按语言分支。</p>
 */
public interface LanguageHandler {

    /** 语言标识，与 {@code Question.language} 对应，如 "Java" / "C" / "C++"。 */
    String language();

    /** 该语言的 Docker 镜像，如 "eclipse-temurin:17" / "gcc:13"（C/C++ 可共用）。 */
    String image();

    /** 学生源码文件名：Java=Solution.java，C=solution.c，C++=solution.cpp。 */
    String sourceFileName();

    /** 判题包装文件名：Java=Main.java，C=main.c，C++=main.cpp。 */
    String wrapperFileName();

    /** 编译命令；解释型语言（Python）返回空列表表示跳过编译。 */
    List<String> compileCommand();

    /** 运行命令，读取 inputFile 作为 stdin。 */
    List<String> runCommand(String inputFile);

    /** METHOD 模式：生成包装代码，调用学生实现的方法。 */
    String generateMethodWrapper(MethodSignature signature, List<String> helperClasses);

    /** DESIGN 模式：生成多方法设计题包装（构造器 + 多个方法调用序列）。 */
    String generateDesignWrapper(List<MethodSignature> methods, String className);

    /** STDIO 模式：生成调用学生 main 的包装。 */
    String generateStdioWrapper();

    /** 数据结构定义文件（ListNode / TreeNode / Node），按签名只注入用到的。 */
    Map<String, String> helperSources(MethodSignature signature);
}
