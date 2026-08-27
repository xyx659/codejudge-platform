package com.codejudge.platform.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CodeRunnerTest {

    private final CodeRunner codeRunner = new CodeRunner();

    @Test
    void 解析标量签名() {
        CodeRunner.MethodSignature sig = codeRunner.parseSignature("int sum(int, int)");
        assertEquals("int", sig.returnType());
        assertEquals("sum", sig.methodName());
        assertEquals(List.of("int", "int"), sig.paramTypes());
    }

    @Test
    void 解析数组与泛型签名() {
        CodeRunner.MethodSignature sig = codeRunner.parseSignature("int[] twoSum(int[], int)");
        assertEquals("int[]", sig.returnType());
        assertEquals("twoSum", sig.methodName());
        assertEquals(List.of("int[]", "int"), sig.paramTypes());

        CodeRunner.MethodSignature sig2 = codeRunner.parseSignature(
                "List<List<Integer>> levelOrder(List<List<Integer>>)");
        assertEquals("List<List<Integer>>", sig2.returnType());
        assertEquals("levelOrder", sig2.methodName());
        assertEquals(List.of("List<List<Integer>>"), sig2.paramTypes());
    }

    @Test
    void 解析无参签名() {
        CodeRunner.MethodSignature sig = codeRunner.parseSignature("void run()");
        assertEquals("void", sig.returnType());
        assertEquals("run", sig.methodName());
        assertTrue(sig.paramTypes().isEmpty());
    }

    @Test
    void 生成Main包含方法调用与入参解析() {
        String main = codeRunner.generateMain(codeRunner.parseSignature("int sum(int, int)"));
        assertTrue(main.contains("public class Main"));
        assertTrue(main.contains("new Solution().sum(a0, a1)"));
        assertTrue(main.contains("Integer.parseInt(value(p[0]))"));
        assertTrue(main.contains("System.out.println(r)"));
    }

    @Test
    void 实际编译运行标量单用例() throws Exception {
        assumeTrue(hasCommand("javac") && hasCommand("java"), "环境缺少 javac/java，跳过真实编译运行");

        String output = runGenerated(
                "class Solution { public int sum(int a, int b) { return a + b; } }",
                "int sum(int, int)",
                "a = 1, b = 2\n");
        assertEquals("3", output.trim());
    }

    @Test
    void 实际编译运行数组单用例() throws Exception {
        assumeTrue(hasCommand("javac") && hasCommand("java"), "环境缺少 javac/java，跳过真实编译运行");

        String output = runGenerated(
                "class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0, 1}; } }",
                "int[] twoSum(int[], int)",
                "nums = [2,7,11,15], target = 9\n");
        assertEquals("[0, 1]", output.trim());
    }

    private String runGenerated(String solution, String signature, String input) throws Exception {
        Path tmp = Files.createTempDirectory("code-runner-test-");
        Files.writeString(tmp.resolve("Solution.java"), solution);
        Files.writeString(tmp.resolve("Main.java"),
                codeRunner.generateMain(codeRunner.parseSignature(signature)));

        Process compile = new ProcessBuilder("javac", "-encoding", "UTF-8", "Solution.java", "Main.java")
                .directory(tmp.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(compile.waitFor(30, TimeUnit.SECONDS), "javac 超时");
        String compileLog = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, compile.exitValue(), "编译失败:\n" + compileLog);

        Process run = new ProcessBuilder("java", "-cp", ".", "Main")
                .directory(tmp.toFile())
                .start();
        run.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        run.getOutputStream().close();
        String stdout = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(30, TimeUnit.SECONDS), "java 超时");
        assertEquals(0, run.exitValue(), "运行失败:\n" + stderr);
        return stdout;
    }

    private boolean hasCommand(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "-version").redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}