package com.codejudge.platform.service;

import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Step 8 联调验证：使用真实 Docker 沙箱跑通一条 Java 提交，
 * 覆盖「编译错误 / 正常通过 / 运行时超时 / 运行时错误」四类场景。
 *
 * <p>仓库与配置均为 mock（聚焦真实 Docker 判题链路），
 * 仅 Docker 客户端、{@link JudgeContainerClient}、{@link CodeRunner}、{@link WorkspacePacker}
 * 与 {@link DockerJudgeEngine} 为真实实现。</p>
 *
 * <p>前置条件：宿主机已安装 Docker 且本地存在 {@code eclipse-temurin:17} 镜像。</p>
 */
@ExtendWith(MockitoExtension.class)
class DockerJudgeEngineIntegrationTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionDetailRepository submissionDetailRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private AiReviewService aiReviewService;

    private DockerJudgeEngine engine;
    private Submission submission;
    private SubmissionDetail detail;
    private Question question;
    private DockerClient dockerClient;

    @BeforeEach
    void setUp() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);

        JudgeContainerClient containerClient =
                new JudgeContainerClient(dockerClient, "eclipse-temurin:17");
        engine = new DockerJudgeEngine(submissionRepository, submissionDetailRepository,
                questionRepository, systemConfigService, new CodeRunner(), new WorkspacePacker(),
                containerClient, aiReviewService);

        submission = new Submission("q1", 100L);
        detail = new SubmissionDetail();
        detail.setSubmissionId(1L);
        detail.setStudentId(100L);
        question = new Question();
        question.setTitle("两数之和");
        question.setDescription("返回 a + b");
    }

    private void stub(String sourceCode, String signature, List<QuestionTestCase> cases) {
        detail.setSourceCode(sourceCode);
        question.setMethodSignature(signature);
        question.setTestCases(cases);

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(submissionDetailRepository.findBySubmissionIdAndStudentId(1L, 100L))
                .thenReturn(Optional.of(detail));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(question));
        when(systemConfigService.getJudgeRuntimeConfig())
                .thenReturn(new JudgeRuntimeConfig(2000, 256, 10));
    }

    @Test
    void 编译错误回写COMPILE_ERROR() {
        stub("public class Solution { public int sum(int a, int b { return a + b; } }",
                "int sum(int, int)",
                List.of(new QuestionTestCase("基本用例", "a = 1, b = 2", "3")));

        engine.judge(1L);

        assertEquals("COMPILE_ERROR", submission.getJudgeStatus());
        assertEquals(0, submission.getScore().intValue());
        assertEquals("COMPILE_ERROR", detail.getJudgeStatus());
        assertEquals(0, detail.getScore().intValue());
    }

    @Test
    void 正常通过回写RUN_COMPLETED() {
        stub("public class Solution { public int sum(int a, int b) { return a + b; } }",
                "int sum(int, int)",
                List.of(new QuestionTestCase("基本用例", "a = 1, b = 2", "3")));

        engine.judge(1L);

        assertEquals("RUN_COMPLETED", submission.getJudgeStatus());
        assertEquals(100, submission.getScore().intValue());
        assertEquals("RUN_COMPLETED", detail.getJudgeStatus());
        assertEquals(1, detail.getTestResults().size());
        assertTrue(detail.getTestResults().get(0).isPassed());
    }

    @Test
    void 运行时死循环被判超时() {
        stub("public class Solution { public int sum(int a, int b) { while (true) {} } }",
                "int sum(int, int)",
                List.of(new QuestionTestCase("死循环", "a = 1, b = 2", "3")));

        engine.judge(1L);

        assertEquals("RUN_COMPLETED", submission.getJudgeStatus());
        assertEquals(0, submission.getScore().intValue());
        assertFalse(detail.getTestResults().get(0).isPassed());
        assertTrue(detail.getTestResults().get(0).getMessage().contains("超时"),
                "用例结果应标记为超时");
    }

    @Test
    void 运行时异常回写运行时错误() {
        stub("public class Solution { public int sum(int a, int b) { String s = null; return s.length(); } }",
                "int sum(int, int)",
                List.of(new QuestionTestCase("空指针", "a = 1, b = 2", "3")));

        engine.judge(1L);

        assertEquals("RUN_COMPLETED", submission.getJudgeStatus());
        assertEquals(0, submission.getScore().intValue());
        assertFalse(detail.getTestResults().get(0).isPassed());
        assertTrue(detail.getTestResults().get(0).getMessage().contains("运行时错误"),
                "用例结果应标记为运行时错误");
    }
}