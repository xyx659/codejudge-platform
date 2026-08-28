package com.codejudge.platform.service;

import com.codejudge.platform.entity.AiReview;
import com.codejudge.platform.entity.TestCaseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 白盒评审服务：调用 OpenAI 兼容的 Chat Completions 接口，
 * 对学生代码做代码质量分析，产出 {@link AiReview}。
 *
 * <p>未配置 API Key、接口调用失败或响应解析失败时，统一返回 {@code null}
 * 表示「跳过评审」，绝不向上抛异常影响黑盒判题链路。</p>
 */
@Service
public class AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewService.class);

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiReviewService(SystemConfigService systemConfigService, ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 读取当前 AI 运行配置，供后续 AI 评审实现使用 */
    public AiRuntimeConfig currentConfig() {
        return systemConfigService.getAiRuntimeConfig();
    }

    /** 记录当前模型配置，不输出 API Key */
    public void logCurrentConfig(Long submissionId) {
        AiRuntimeConfig config = currentConfig();
        log.info(
                "AI评审配置已加载：submissionId={}, provider={}, model={}, baseUrl={}, hasApiKey={}",
                submissionId,
                config.provider(),
                config.model(),
                config.baseUrl(),
                config.apiKey() != null && !config.apiKey().isBlank());
    }

    /**
     * 触发一次白盒评审。
     *
     * @return 评审结果；未配置 Key、调用失败或解析失败时返回 {@code null}（跳过）
     */
    public AiReview review(String questionTitle, String questionDescription, String methodSignature,
                           String sourceCode, int passRate, List<TestCaseResult> testResults) {
        AiRuntimeConfig config = currentConfig();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            log.info("未配置 AI API Key，跳过白盒评审：passRate={}", passRate);
            return null;
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            log.info("源码为空，跳过白盒评审");
            return null;
        }
        try {
            String prompt = buildPrompt(questionTitle, questionDescription, methodSignature,
                    sourceCode, passRate, testResults);
            String content = callChatCompletions(config, prompt);
            return parseReview(content, passRate);
        } catch (Exception e) {
            log.warn("AI 评审失败，跳过：{}", e.getMessage());
            return null;
        }
    }

    /** 组装 Prompt：题目信息 + 学生源码 + 黑盒结果，要求 AI 只回 JSON。 */
    private String buildPrompt(String title, String description, String signature,
                               String sourceCode, int passRate, List<TestCaseResult> results) {
        StringBuilder cases = new StringBuilder();
        for (TestCaseResult r : results) {
            String name = r.getTestCaseName() == null || r.getTestCaseName().isBlank()
                    ? "用例" : r.getTestCaseName();
            cases.append("- ").append(name).append("：")
                    .append(r.isPassed() ? "通过" : "未通过（" + safeMessage(r) + "）")
                    .append('\n');
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位严谨的 Java 编程评审老师，请对下面的学生代码做白盒代码质量评审。\n\n");
        sb.append("【题目】\n");
        sb.append("标题：").append(nullToEmpty(title)).append('\n');
        sb.append("描述：").append(nullToEmpty(description)).append('\n');
        sb.append("方法签名：").append(nullToEmpty(signature)).append('\n');
        sb.append("\n【学生提交的代码】\n```java\n").append(sourceCode).append("\n```\n");
        sb.append("\n【黑盒测试结果】\n用例通过率：").append(passRate).append(" / 100\n").append(cases);
        sb.append("\n请只输出一个 JSON 对象，不要包含任何额外文字或 Markdown 代码块标记，格式如下：\n");
        sb.append("{\"qualityScore\": 0到100的整数, \"feedback\": [\"建议1\", \"建议2\", \"...\"]}\n\n");
        sb.append("qualityScore 是对代码正确性、命名、结构、边界处理、时间复杂度的综合质量评分；feedback 给出 2~5 条具体可操作的改进建议。");
        return sb.toString();
    }

    /** 调用 OpenAI 兼容 Chat Completions，返回首个 choice 的文本内容。 */
    private String callChatCompletions(AiRuntimeConfig config, String prompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是一位严谨的 Java 编程评审老师。"),
                Map.of("role", "user", "content", prompt)));

        String url = config.baseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("AI 接口返回 " + response.statusCode()
                    + "：" + abbreviate(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("AI 响应缺少 choices[0].message.content");
        }
        return content.asText();
    }

    /** 解析 AI 输出 JSON，计算综合分并组装 AiReview。 */
    private AiReview parseReview(String content, int passRate) throws Exception {
        JsonNode node = objectMapper.readTree(extractJsonObject(content));
        int qualityScore = clamp(node.path("qualityScore").asInt(0), 0, 100);

        List<String> feedback = new ArrayList<>();
        JsonNode feedbackNode = node.get("feedback");
        if (feedbackNode != null && feedbackNode.isArray()) {
            for (JsonNode item : feedbackNode) {
                if (item != null && item.isTextual()) {
                    feedback.add(item.asText());
                }
            }
        }

        int score = (int) Math.round(passRate * 0.7 + qualityScore * 0.3);
        return new AiReview(score, passRate, qualityScore, feedback);
    }

    /** 从 AI 输出中截取首个 JSON 对象（容忍 Markdown 代码块包裹与前后杂文）。 */
    private String extractJsonObject(String content) {
        String s = content.trim()
                .replaceAll("^```[a-zA-Z]*\\s*", "")
                .replaceAll("\\s*```$", "");
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI 输出未包含 JSON 对象");
        }
        return s.substring(start, end + 1);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String safeMessage(TestCaseResult r) {
        String message = r.getMessage();
        return message == null || message.isBlank() ? "未通过" : message;
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}