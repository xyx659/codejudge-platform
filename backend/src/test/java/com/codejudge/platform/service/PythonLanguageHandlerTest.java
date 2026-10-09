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

class PythonLanguageHandlerTest {

    private final PythonLanguageHandler handler = new PythonLanguageHandler();

    @Test
    void 解析原生签名() {
        MethodSignature sig = handler.parseSignature(
                "def twoSum(self, nums: List[int], target: int) -> List[int]");
        assertEquals("List[int]", sig.returnType());
        assertEquals("twoSum", sig.methodName());
        assertEquals(List.of("List[int]", "int"), sig.paramTypes());
    }

    @Test
    void 解析省略def与self的签名() {
        MethodSignature sig = handler.parseSignature("twoSum(nums: List[int], target: int) -> List[int]");
        assertEquals("List[int]", sig.returnType());
        assertEquals("twoSum", sig.methodName());
        assertEquals(List.of("List[int]", "int"), sig.paramTypes());
    }

    @Test
    void 解析无返回签名() {
        MethodSignature sig = handler.parseSignature("def run(self, a: int) -> None");
        assertEquals("void", sig.returnType());
        assertEquals("run", sig.methodName());
        assertEquals(List.of("int"), sig.paramTypes());
    }

    @Test
    void 实际运行标量单用例() throws Exception {
        assumeTrue(hasCommand("python3"), "环境缺少 python3，跳过真实运行");

        String output = runGenerated(
                "class Solution:\n    def sum(self, a: int, b: int) -> int:\n        return a + b\n",
                "def sum(self, a: int, b: int) -> int",
                "a = 1, b = 2\n");
        assertEquals("3", output.trim());
    }

    @Test
    void 实际运行数组单用例() throws Exception {
        assumeTrue(hasCommand("python3"), "环境缺少 python3，跳过真实运行");

        String output = runGenerated(
                "class Solution:\n"
                        + "    def twoSum(self, nums: List[int], target: int) -> List[int]:\n"
                        + "        for i in range(len(nums)):\n"
                        + "            for j in range(i + 1, len(nums)):\n"
                        + "                if nums[i] + nums[j] == target:\n"
                        + "                    return [i, j]\n"
                        + "        return []\n",
                "def twoSum(self, nums: List[int], target: int) -> List[int]",
                "nums = [2,7,11,15], target = 9\n");
        assertEquals("[0,1]", output.trim());
    }

    @Test
    void 实际运行链表单用例() throws Exception {
        assumeTrue(hasCommand("python3"), "环境缺少 python3，跳过真实运行");

        String output = runGenerated(
                "class Solution:\n"
                        + "    def reverseList(self, head: Optional[ListNode]) -> Optional[ListNode]:\n"
                        + "        prev = None\n"
                        + "        cur = head\n"
                        + "        while cur:\n"
                        + "            nxt = cur.next\n"
                        + "            cur.next = prev\n"
                        + "            prev = cur\n"
                        + "            cur = nxt\n"
                        + "        return prev\n",
                "def reverseList(self, head: Optional[ListNode]) -> Optional[ListNode]",
                "head = [1,2,3,4,5]\n");
        assertEquals("[5,4,3,2,1]", output.trim());
    }

    @Test
    void 实际运行树单用例() throws Exception {
        assumeTrue(hasCommand("python3"), "环境缺少 python3，跳过真实运行");

        String output = runGenerated(
                "class Solution:\n"
                        + "    def maxDepth(self, root: Optional[TreeNode]) -> int:\n"
                        + "        if not root:\n"
                        + "            return 0\n"
                        + "        return 1 + max(self.maxDepth(root.left), self.maxDepth(root.right))\n",
                "def maxDepth(self, root: Optional[TreeNode]) -> int",
                "root = [3,9,20,null,null,15,7]\n");
        assertEquals("3", output.trim());
    }

    private String runGenerated(String solution, String signature, String input) throws Exception {
        Path tmp = Files.createTempDirectory("python-handler-test-");
        Files.writeString(tmp.resolve("solution.py"), solution);
        Files.writeString(tmp.resolve("main.py"),
                handler.generateMethodWrapper(handler.parseSignature(signature), List.of()));

        Process run = new ProcessBuilder("python3", "main.py")
                .directory(tmp.toFile())
                .start();
        run.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        run.getOutputStream().close();
        String stdout = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(30, TimeUnit.SECONDS), "python3 超时");
        assertEquals(0, run.exitValue(), "运行失败:\n" + stderr);
        return stdout;
    }

    private boolean hasCommand(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version").redirectErrorStream(true).start();
            return p.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}
