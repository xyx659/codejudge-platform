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

class GoLanguageHandlerTest {

    private final GoLanguageHandler handler = new GoLanguageHandler();

    @Test
    void 解析原生签名() {
        MethodSignature sig = handler.parseSignature("twoSum(nums []int, target int) []int");
        assertEquals("[]int", sig.returnType());
        assertEquals("twoSum", sig.methodName());
        assertEquals(List.of("[]int", "int"), sig.paramTypes());
    }

    @Test
    void 解析无返回签名() {
        MethodSignature sig = handler.parseSignature("run(a int)");
        assertEquals("void", sig.returnType());
        assertEquals("run", sig.methodName());
        assertEquals(List.of("int"), sig.paramTypes());
    }

    @Test
    void 解析结构签名() {
        MethodSignature sig = handler.parseSignature("reverseList(head *ListNode) *ListNode");
        assertEquals("*ListNode", sig.returnType());
        assertEquals("reverseList", sig.methodName());
        assertEquals(List.of("*ListNode"), sig.paramTypes());
    }

    @Test
    void 实际编译运行标量() throws Exception {
        assumeTrue(hasGo(), "环境缺少 go，跳过真实编译运行");

        String output = compileAndRun(
                "package main\n\nfunc sum(a int, b int) int { return a + b }\n",
                "sum(a int, b int) int",
                "a = 1, b = 2\n");
        assertEquals("3", output.trim());
    }

    @Test
    void 实际编译运行数组() throws Exception {
        assumeTrue(hasGo(), "环境缺少 go，跳过真实编译运行");

        String output = compileAndRun(
                "package main\n\nfunc twoSum(nums []int, target int) []int {\n"
                        + "    for i := 0; i < len(nums); i++ {\n"
                        + "        for j := i + 1; j < len(nums); j++ {\n"
                        + "            if nums[i]+nums[j] == target {\n"
                        + "                return []int{i, j}\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    return []int{}\n"
                        + "}\n",
                "twoSum(nums []int, target int) []int",
                "nums = [2,7,11,15], target = 9\n");
        assertEquals("[0,1]", output.trim());
    }

    @Test
    void 实际编译运行字符串列表() throws Exception {
        assumeTrue(hasGo(), "环境缺少 go，跳过真实编译运行");

        String output = compileAndRun(
                "package main\n\nfunc firstStr(strs []string) string {\n"
                        + "    if len(strs) == 0 { return \"\" }\n"
                        + "    return strs[0]\n"
                        + "}\n",
                "firstStr(strs []string) string",
                "strs = [\"flower\",\"flow\",\"flight\"]\n");
        assertEquals("\"flower\"", output.trim());
    }

    @Test
    void 实际编译运行链表() throws Exception {
        assumeTrue(hasGo(), "环境缺少 go，跳过真实编译运行");

        String output = compileAndRun(
                "package main\n\nfunc reverseList(head *ListNode) *ListNode {\n"
                        + "    var prev *ListNode\n"
                        + "    cur := head\n"
                        + "    for cur != nil {\n"
                        + "        nxt := cur.Next\n"
                        + "        cur.Next = prev\n"
                        + "        prev = cur\n"
                        + "        cur = nxt\n"
                        + "    }\n"
                        + "    return prev\n"
                        + "}\n",
                "reverseList(head *ListNode) *ListNode",
                "head = [1,2,3,4,5]\n");
        assertEquals("[5,4,3,2,1]", output.trim());
    }

    @Test
    void 实际编译运行树() throws Exception {
        assumeTrue(hasGo(), "环境缺少 go，跳过真实编译运行");

        String output = compileAndRun(
                "package main\n\nfunc maxDepth(root *TreeNode) int {\n"
                        + "    if root == nil { return 0 }\n"
                        + "    l := maxDepth(root.Left)\n"
                        + "    r := maxDepth(root.Right)\n"
                        + "    if l > r { return l + 1 }\n"
                        + "    return r + 1\n"
                        + "}\n",
                "maxDepth(root *TreeNode) int",
                "root = [3,9,20,null,null,15,7]\n");
        assertEquals("3", output.trim());
    }

    private String compileAndRun(String solution, String signature, String input) throws Exception {
        Path tmp = Files.createTempDirectory("go-handler-test-");
        Files.writeString(tmp.resolve("solution.go"), solution);
        Files.writeString(tmp.resolve("main.go"),
                handler.generateMethodWrapper(handler.parseSignature(signature), List.of()));

        String gocache = tmp.resolve("gocache").toString();
        Process compile = new ProcessBuilder("sh", "-c",
                "export GOCACHE=" + gocache + "; go mod init judge >/dev/null 2>&1; go build -o solution .")
                .directory(tmp.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(compile.waitFor(60, TimeUnit.SECONDS), "go build 超时");
        String compileLog = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, compile.exitValue(), "编译失败:\n" + compileLog);

        Files.writeString(tmp.resolve("input.txt"), input);
        Process run = new ProcessBuilder("sh", "-c", "chmod +x solution && ./solution < input.txt")
                .directory(tmp.toFile())
                .start();
        String stdout = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(30, TimeUnit.SECONDS), "运行超时");
        assertEquals(0, run.exitValue(), "运行失败:\n" + stderr);
        return stdout;
    }

    private boolean hasGo() {
        try {
            Process p = new ProcessBuilder("go", "version").redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}
