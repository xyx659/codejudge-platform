package com.codejudge.platform.service;

import com.codejudge.platform.dto.RunRequest;
import com.codejudge.platform.dto.RunResult;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.repository.QuestionRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Python / Go 端到端联调验证：用真实 Docker 沙箱 + 真实 {@link LanguageHandler} 跑通
 * 一条提交，覆盖「正常通过 / 编译错误 / 输出不符」三类场景。
 *
 * <p>仓库与配置均为 mock（聚焦真实 Docker 判题链路），仅 Docker 客户端、
 * {@link JudgeContainerClient}、{@link LanguageHandlerRegistry}、{@link WorkspacePacker}
 * 与 {@link RunService} 为真实实现。</p>
 *
 * <p>前置条件：宿主机已安装 Docker，且本地存在
 * {@code python:3.12-alpine} 与 {@code golang:1.22-alpine} 镜像。</p>
 */
@ExtendWith(MockitoExtension.class)
class MultiLanguageRunIntegrationTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private SystemConfigService systemConfigService;

    private RunService runService;

    @BeforeEach
    void setUp() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        LanguageHandlerRegistry registry = new LanguageHandlerRegistry(List.of(
                new JavaLanguageHandler(),
                new PythonLanguageHandler(),
                new GoLanguageHandler()));
        runService = new RunService(questionRepository, systemConfigService,
                registry, new WorkspacePacker(),
                new JudgeContainerClient(dockerClient));

        when(systemConfigService.getJudgeRuntimeConfig())
                .thenReturn(new JudgeRuntimeConfig(2000, 256, 10));
    }

    private void stubQuestion(String language, String signature, String input, String expected) {
        Question question = new Question();
        question.setLanguage(language);
        question.setJudgeMode("METHOD");
        question.setMethodSignature(signature);
        question.setTestCases(List.of(new QuestionTestCase("样例", input, expected)));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(question));
    }

    @Test
    void python两数之和返回列表() {
        stubQuestion("Python",
                "def twoSum(self, nums: List[int], target: int) -> List[int]",
                "nums = [2,7,11,15], target = 9",
                "[0,1]");
        String source = "class Solution:\n"
                + "    def twoSum(self, nums, target):\n"
                + "        d = {}\n"
                + "        for i, n in enumerate(nums):\n"
                + "            if target - n in d:\n"
                + "                return [d[target - n], i]\n"
                + "            d[n] = i\n"
                + "        return []\n";

        RunResult result = runService.run(new RunRequest("q1", source, null));

        assertNull(result.compileError(), "Python 解释型无编译步骤，compileError 应为 null");
        assertEquals(1, result.results().size());
        assertTrue(result.results().get(0).passed(), "两数之和应判为通过");
        assertEquals("[0,1]", result.results().get(0).actual());
    }

    @Test
    void python标量求和() {
        stubQuestion("Python",
                "def add(self, a: int, b: int) -> int",
                "a = 3, b = 4",
                "7");
        String source = "class Solution:\n"
                + "    def add(self, a, b):\n"
                + "        return a + b\n";

        RunResult result = runService.run(new RunRequest("q1", source, null));

        assertNull(result.compileError());
        assertTrue(result.results().get(0).passed());
        assertEquals("7", result.results().get(0).actual());
    }

    @Test
    void go两数之和返回切片() {
        stubQuestion("Go",
                "twoSum(nums []int, target int) []int",
                "nums = [2,7,11,15], target = 9",
                "[0,1]");
        String source = "package main\n\n"
                + "func twoSum(nums []int, target int) []int {\n"
                + "    m := map[int]int{}\n"
                + "    for i, n := range nums {\n"
                + "        if j, ok := m[target-n]; ok {\n"
                + "            return []int{j, i}\n"
                + "        }\n"
                + "        m[n] = i\n"
                + "    }\n"
                + "    return nil\n"
                + "}\n";

        RunResult result = runService.run(new RunRequest("q1", source, null));

        assertNull(result.compileError(), "Go 编译应成功，compileError 应为 null");
        assertEquals(1, result.results().size());
        assertTrue(result.results().get(0).passed(), "两数之和应判为通过");
        assertEquals("[0,1]", result.results().get(0).actual());
    }

    @Test
    void go标量求和() {
        stubQuestion("Go",
                "add(a int, b int) int",
                "a = 3, b = 4",
                "7");
        String source = "package main\n\n"
                + "func add(a int, b int) int {\n"
                + "    return a + b\n"
                + "}\n";

        RunResult result = runService.run(new RunRequest("q1", source, null));

        assertNull(result.compileError());
        assertTrue(result.results().get(0).passed());
        assertEquals("7", result.results().get(0).actual());
    }

    @Test
    void go编译错误返回compileError() {
        stubQuestion("Go",
                "add(a int, b int) int",
                "a = 3, b = 4",
                "7");
        // 缺右括号，无法编译
        String source = "package main\n\n"
                + "func add(a int, b int) int {\n"
                + "    return a + b\n";

        RunResult result = runService.run(new RunRequest("q1", source, null));

        assertTrue(result.compileError() != null && !result.compileError().isBlank(),
                "Go 编译错误应返回 compileError");
        assertTrue(result.results().isEmpty(), "编译失败时 results 应为空");
    }

    @Test
    void python输出不符标记未通过() {
        stubQuestion("Python",
                "def add(self, a: int, b: int) -> int",
                "a = 3, b = 4",
                "8");
        String source = "class Solution:\n"
                + "    def add(self, a, b):\n"
                + "        return a + b\n";

        RunResult result = runService.run(new RunRequest("q1", source, null));

        assertNull(result.compileError());
        assertFalse(result.results().get(0).passed(), "3+4=7 不等于期望 8，应判未通过");
        assertEquals("7", result.results().get(0).actual());
    }
}
