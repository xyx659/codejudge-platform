package com.codejudge.platform.service;

import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.TestCaseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Step 7 端到端验证：走真实 {@link AiReviewService#review} 调用外部大模型。
 *
 * <p>前置条件：系统已配置 AI API Key（{@code ai.api_key}），且当前网络可访问
 * {@code ai.base_url}（如需要 VPN）。未配置 Key 时跳过，不阻断日常构建。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AiReviewServiceIntegrationTest {

    @Autowired
    private AiReviewService aiReviewService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Test
    void 真实调用大模型返回合法评审() {
        AiRuntimeConfig config = systemConfigService.getAiRuntimeConfig();
        assumeTrue(config.apiKey() != null && !config.apiKey().isBlank(),
                "未配置 AI API Key，跳过真实调用");

        AiReview review = aiReviewService.review(
                "两数之和",
                "给定两个整数 a、b，返回 a + b",
                "int sum(int, int)",
                "public class Solution { public int sum(int a, int b) { return a + b; } }",
                100,
                List.of(new TestCaseResult("基本用例", true, "3", "通过", 5L)));

        assertNotNull(review, "配置了 Key 时应返回评审结果");
        assertEquals(100, review.getPassRate().intValue(), "通过率应原样回填");
        assertTrue(review.getQualityScore() >= 0 && review.getQualityScore() <= 100,
                "质量分应在 0~100 之间");
        assertTrue(review.getScore() >= 0 && review.getScore() <= 100,
                "综合分应在 0~100 之间");
        assertFalse(review.getFeedback().isEmpty(), "应至少给出一条反馈");
    }
}