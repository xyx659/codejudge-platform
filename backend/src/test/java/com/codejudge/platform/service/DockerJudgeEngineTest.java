package com.codejudge.platform.service;

import com.codejudge.platform.dto.ContainerRunResult;
import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DockerJudgeEngine} 的结果回写单测（mock 容器与仓库）：
 * 覆盖正常通过、编译失败、编译超时、以及异常兜底四类回写路径。
 */
@ExtendWith(MockitoExtension.class)
class DockerJudgeEngineTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionDetailRepository submissionDetailRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private JudgeContainerClient containerClient;
    @Mock
    private AiReviewService aiReviewService;

    private WorkspacePacker packer;
    private DockerJudgeEngine engine;

    private Submission submission;
    private SubmissionDetail detail;
    private Question question;

    @BeforeEach
    void setUp() {
        packer = new WorkspacePacker();
        engine = new DockerJudgeEngine(submissionRepository, submissionDetailRepository,
                questionRepository, systemConfigService, new CodeRunner(), packer, containerClient,
                aiReviewService);

        submission = new Submission("q1", 100L);
        detail = new SubmissionDetail();
        detail.setSubmissionId(1L);
        detail.setStudentId(100L);
        detail.setSourceCode("class Solution { public int sum(int a, int b) { return a + b; } }");

        question = new Question();
        question.setMethodSignature("int sum(int, int)");
        question.setTestCases(List.of(new QuestionTestCase("基本用例", "a = 1, b = 2", "3")));
    }

    private void stubCommon() {
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(submissionDetailRepository.findBySubmissionIdAndStudentId(1L, 100L))
                .thenReturn(Optional.of(detail));
        when(questionRepository.findById("q1")).thenReturn(Optional.of(question));
        when(systemConfigService.getJudgeRuntimeConfig()).thenReturn(new JudgeRuntimeConfig(1000, 256, 10));
    }

    @Test
    void 编译成功用例通过时正确回写且不动aiReview() {
        AiReview preset = new AiReview(90, 100, 80, List.of("ok"));
        detail.setAiReview(preset);
        stubCommon();
        when(containerClient.compile(any()))
                .thenReturn(new JudgeContainerClient.CompileResult(
                        0, "", packer.pack(Map.of("Solution.class", new byte[0])), false));
        when(containerClient.run(any()))
                .thenReturn(new ContainerRunResult(0, "3\n", "", false));

        engine.judge(1L);

        assertEquals("RUN_COMPLETED", submission.getJudgeStatus());
        assertEquals(100, submission.getScore().intValue());
        assertEquals("RUN_COMPLETED", detail.getJudgeStatus());
        assertEquals(100, detail.getScore().intValue());
        assertEquals(1, detail.getTestResults().size());
        assertTrue(detail.getTestResults().get(0).isPassed());
        assertSame(preset, detail.getAiReview(), "回写不应改动 aiReview");

        verify(aiReviewService).review(any(), any(), any(), any(), anyInt(), anyList());

        verify(submissionRepository).save(submission);
        verify(submissionDetailRepository).save(detail);
    }

    @Test
    void 编译成功且AI评审返回时回写aiReview() {
        stubCommon();
        when(containerClient.compile(any()))
                .thenReturn(new JudgeContainerClient.CompileResult(
                        0, "", packer.pack(Map.of("Solution.class", new byte[0])), false));
        when(containerClient.run(any())).thenReturn(new ContainerRunResult(0, "3\n", "", false));
        AiReview review = new AiReview(93, 100, 90, List.of("建议补充边界处理"));
        when(aiReviewService.review(any(), any(), any(), any(), anyInt(), anyList()))
                .thenReturn(review);

        engine.judge(1L);

        assertSame(review, detail.getAiReview());
    }

    @Test
    void 编译失败时回写COMPILE_ERROR且得分0() {
        stubCommon();
        when(containerClient.compile(any()))
                .thenReturn(new JudgeContainerClient.CompileResult(
                        1, "error: cannot find symbol", null, false));

        engine.judge(1L);

        assertEquals("COMPILE_ERROR", submission.getJudgeStatus());
        assertEquals(0, submission.getScore().intValue());
        assertEquals("COMPILE_ERROR", detail.getJudgeStatus());
        assertEquals(0, detail.getScore().intValue());

        verify(submissionRepository).save(submission);
        verify(submissionDetailRepository).save(detail);
    }

    @Test
    void 编译超时回写TIMEOUT且得分0() {
        stubCommon();
        when(containerClient.compile(any()))
                .thenReturn(new JudgeContainerClient.CompileResult(-1, "", null, true));

        engine.judge(1L);

        assertEquals("TIMEOUT", submission.getJudgeStatus());
        assertEquals(0, submission.getScore().intValue());
        assertEquals("TIMEOUT", detail.getJudgeStatus());
        assertEquals(0, detail.getScore().intValue());
    }

    @Test
    void 评测异常时把两库都标为COMPILE_ERROR() {
        stubCommon();
        when(containerClient.compile(any())).thenThrow(new RuntimeException("boom"));

        engine.judge(1L);

        assertEquals("COMPILE_ERROR", submission.getJudgeStatus());
        assertEquals(0, submission.getScore().intValue());
        assertEquals("COMPILE_ERROR", detail.getJudgeStatus());
        assertEquals(0, detail.getScore().intValue());

        verify(submissionRepository).save(submission);
        verify(submissionDetailRepository).save(detail);
    }
}