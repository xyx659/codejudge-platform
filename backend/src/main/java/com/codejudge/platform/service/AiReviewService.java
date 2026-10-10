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
import java.util.Locale;
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
                           String language, String sourceCode, int passRate, List<TestCaseResult> testResults) {
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
                    language, sourceCode, passRate, testResults);
            String content = callChatCompletions(config, prompt);
            return parseReview(content, passRate);
        } catch (Exception e) {
            log.warn("AI 评审失败，跳过：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据题目描述 AI 生成：测试用例（20 个）、标签、难度、方法名、方法签名。
     *
     * @param title       题目标题
     * @param description 题目描述
     * @return 生成结果 JSON 字符串（含 testCases / tags / difficulty / methodName / methodSignature）
     */
    public String generateQuestion(String title, String description, String language) {
        AiRuntimeConfig config = currentConfig();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("未配置 AI API Key，无法生成题目");
        }
        String prompt = buildGeneratePrompt(title, description, language);
        try {
            // forGeneration=true：跳过 thinking 模式，加速生成
            return callChatCompletions(config, prompt, true);
        } catch (Exception e) {
            log.error("AI 生成题目失败：{}", e.getMessage());
            throw new IllegalStateException("AI 生成失败：" + e.getMessage());
        }
    }

    public String buildGeneratePrompt(String title, String description, String language) {
        String lang = languageName(language);
        return "根据题目生成判题数据，只输出JSON，无额外文字。\n\n"
             + "标题：" + title + "\n"
             + "描述：" + description + "\n\n"
             + "=== 编程语言（methodSignature / designMethods 必须按该语言原生签名填写）===\n"
             + "当前语言：" + lang + "\n"
             + "- Java：methodSignature 如 \"int[] twoSum(int[], int)\"，designMethods 如 \"int get(int)\"\n"
             + "- Python：methodSignature 如 \"def twoSum(self, nums: List[int], target: int) -> List[int]\"，designMethods 如 \"def get(self, key: int) -> int\"\n"
             + "- Go：methodSignature 如 \"twoSum(nums []int, target int) []int\"，designMethods 如 \"get(key int) int\"\n\n"
             + "=== 判题模式 ===\n"
             + "根据题目类型选择 judgeMode：\n"
             + "- METHOD（默认）：单方法题，需填 methodName + methodSignature\n"
             + "  input格式：参数名=值，如 nums = [2,7,11,15], target = 9\n"
             + "  expected格式：方法返回值，如 [0,1]\n"
             + "- DESIGN：设计题（LRU Cache/MinStack等多方法调用），需填 methodName + designMethods\n"
             + "  designMethods格式：[\"void LRUCache(int)\",\"int get(int)\",\"void put(int,int)\"]\n"
             + "  input格式：两行，第一行方法名JSON数组，第二行参数JSON数组\n"
             + "  expected格式：结果JSON数组，如 [null,null,1,null,-1]\n"
             + "- STDIO：标准输入输出完整程序，只需填 methodName=\"main\"\n"
             + "  input/expected：直接文本\n\n"
             + "=== 合法标签（只能从以下选取2~4个）===\n"
             + "数组, 链表, 栈, 队列, 哈希表, 字符串, 二叉树, 树, 图, 堆（优先队列）\n"
             + "动态规划, 贪心, 回溯, 递归, 分治, 二分查找, 双指针, 滑动窗口, 单调栈\n"
             + "深度优先搜索, 广度优先搜索, 拓扑排序, 并查集, 字典树, 记忆化\n"
             + "位运算, 数学, 几何, 模拟, 矩阵, 排序, 设计\n\n"
             + "=== JSON输出格式 ===\n"
             + "{\n"
             + "  \"judgeMode\": \"METHOD/DESIGN/STDIO\",\n"
             + "  \"methodName\": \"方法名\",\n"
             + "  \"methodSignature\": \"签名（METHOD模式必填）\",\n"
             + "  \"designMethods\": [\"签名1\",\"签名2\"]（DESIGN模式必填）,\n"
             + "  \"difficulty\": \"简单/中等/困难\",\n"
             + "  \"tags\": [\"标签1\",\"标签2\"],\n"
             + "  \"testCases\": [{\"name\":\"用例1\",\"input\":\"...\",\"expected\":\"...\"}]\n"
             + "}\n\n"
             + "=== 要求 ===\n"
             + "- 10个测试用例：3基本+3边界+2极端+2特殊\n"
             + "- 数组完整列出，禁用省略号\n"
             + "- 数组元素>15时用短数组(5个以内)";
    }

    /** 组装 Prompt：题目信息 + 学生源码 + 黑盒结果 + 评分维度，要求 AI 只回 JSON。 */
    private String buildPrompt(String title, String description, String signature,
                               String language, String sourceCode, int passRate, List<TestCaseResult> results) {
        String lang = languageName(language);
        StringBuilder cases = new StringBuilder();
        for (TestCaseResult r : results) {
            String name = r.getTestCaseName() == null || r.getTestCaseName().isBlank()
                    ? "用例" : r.getTestCaseName();
            cases.append("- ").append(name).append("：")
                    .append(r.isPassed() ? "通过" : "未通过（" + safeMessage(r) + "）")
                    .append('\n');
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位严谨的 ").append(lang).append(" 编程评审老师，请对下面的学生代码做白盒代码质量评审。\n\n");
        sb.append("【题目】\n");
        sb.append("标题：").append(nullToEmpty(title)).append('\n');
        sb.append("描述：").append(nullToEmpty(description)).append('\n');
        sb.append("方法签名：").append(nullToEmpty(signature)).append('\n');
        sb.append("\n【学生提交的代码】\n```").append(lang.toLowerCase(Locale.ROOT)).append("\n").append(sourceCode).append("\n```\n");
        sb.append("\n【黑盒测试结果】\n用例通过率：").append(passRate).append(" / 100\n").append(cases);

        sb.append("\n【评分维度与标准】\n");
        sb.append("请对以下 6 个维度分别打分（0-100 整数）。每个维度附有评分参考：\n\n");

        sb.append("1. algorithm_efficiency（算法效率，权重30%）：时间/空间复杂度与算法选择\n");
        sb.append("   100=最优解（如 O(n)/O(1)） 75=次优但合理（如 O(n log n)） 50=可用但低效（如 O(n^2)） 25=很差（如 O(n^3)） 0=极差或死循环（O(n^4)及以上）\n\n");

        sb.append("2. boundary_handling（边界处理，权重20%）：空值、空集合、溢出、特殊输入\n");
        sb.append("   100=全面覆盖边界 75=覆盖主要边界 50=部分覆盖 25=忽略多数边界 0=完全没考虑\n\n");

        sb.append("3. readability（可读性，权重20%）：命名规范、代码结构、注释、格式\n");
        sb.append("   100=命名清晰、结构优雅、有关键注释 75=整体可读 50=一般 25=较难读 0=混乱\n\n");

        sb.append("4. code_structure（代码结构，权重15%）：模块化、职责分离、类设计\n");
        sb.append("   100=高内聚低耦合、方法职责单一 75=结构合理 50=一般 25=臃肿 0=全部堆在一起\n\n");

        sb.append("5. robustness（鲁棒性，权重10%）：异常处理、防御性编程\n");
        sb.append("   100=完善的异常处理和输入校验 75=基本处理 50=部分处理 25=很少处理 0=无任何防御\n\n");

        sb.append("6. best_practices（最佳实践，权重5%）：").append(lang).append(" 规范、数据结构选择、设计模式\n");
        sb.append("   100=完全遵循 ").append(lang).append(" 规范 75=基本遵循 50=一般 25=较多不规范 0=严重违反\n\n");

        sb.append("请只输出一个 JSON 对象，不要包含任何额外文字或 Markdown 代码块标记。\n\n");
        sb.append("=== 输出格式（严格按此结构，不得省略任何字段） ===\n\n");
        sb.append("{\n");
        sb.append("  \"timeComplexity\": \"分析代码中的主要算法，给出时间复杂度，如 O(n)、O(log n)、O(n^2)\",\n");
        sb.append("  \"spaceComplexity\": \"分析代码中的主要算法，给出空间复杂度，如 O(1)、O(n)\",\n");
        sb.append("  \"dimensionScores\": {\n");
        sb.append("    \"algorithm_efficiency\": 0到100的整数,\n");
        sb.append("    \"boundary_handling\": 0到100的整数,\n");
        sb.append("    \"readability\": 0到100的整数,\n");
        sb.append("    \"code_structure\": 0到100的整数,\n");
        sb.append("    \"robustness\": 0到100的整数,\n");
        sb.append("    \"best_practices\": 0到100的整数\n");
        sb.append("  },\n");
        sb.append("  \"feedback\": [\"具体可操作的改进建议1\", \"建议2\", \"建议3\"],\n");
        sb.append("  \"summary\": \"一段总评语，概括代码整体质量、主要优点和改进方向\"\n");
        sb.append("}\n\n");
        sb.append("注意：\n");
        sb.append("- timeComplexity 和 spaceComplexity 是最优先必填字段，必须是类似 O(n) 的格式字符串\n");
        sb.append("- feedback 必须给出 3~5 条具体可操作的改进建议，不能泛泛而谈\n");
        sb.append("- 低效算法（如 O(n^2) 可用 O(n) 解决时）必须在 feedback 中指出优化方向");
        return sb.toString();
    }

    /** 调用 OpenAI 兼容 Chat Completions，返回首个 choice 的文本内容。 */
    private String callChatCompletions(AiRuntimeConfig config, String prompt) throws Exception {
        return callChatCompletions(config, prompt, false);
    }

    /**
     * 调用 OpenAI 兼容 Chat Completions。
     *
     * @param forGeneration true=生成题目用（跳过thinking，更快）; false=评审用（保留thinking）
     */
    private String callChatCompletions(AiRuntimeConfig config, String prompt, boolean forGeneration) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", 0.2);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是一位严谨的编程评审老师。"),
                Map.of("role", "user", "content", prompt)));
        // deepseek-v4-pro 需要 thinking 和 reasoning_effort 参数
        // 生成题目时跳过 thinking 以加速（评审时保留）
        if (!forGeneration && config.model() != null && config.model().contains("v4-pro")) {
            body.put("thinking", Map.of("type", "enabled"));
            body.put("reasoning_effort", "high");
        }

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
        JsonNode message = root.path("choices").path(0).path("message");

        // 优先取 content；某些 thinking 模型可能把实际内容放在 reasoning_content 中
        JsonNode content = message.path("content");
        String result = null;
        if (!content.isMissingNode() && !content.isNull()) {
            result = content.asText();
        }

        // content 为空时，尝试从 reasoning_content 获取（部分 thinking 模型的兼容字段）
        if (result == null || result.isBlank()) {
            JsonNode reasoning = message.path("reasoning_content");
            if (!reasoning.isMissingNode() && !reasoning.isNull()) {
                result = reasoning.asText();
                log.info("content 为空，使用 reasoning_content 作为 AI 响应");
            }
        }

        if (result == null || result.isBlank()) {
            log.warn("AI 响应原始内容：{}", abbreviate(response.body()));
            throw new IllegalStateException("AI 响应缺少有效内容");
        }
        return result;
    }

    /**
     * 流式调用 AI 生成题目，通过回调逐块返回内容。
     *
     * @param config   AI 配置
     * @param prompt   提示词
     * @param onChunk  每收到一块内容时的回调（参数为累积的完整文本）
     * @return 最终完整文本
     */
    public String streamGenerate(AiRuntimeConfig config, String prompt,
                                  java.util.function.Consumer<String> onChunk) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", 0.2);
        body.put("stream", true);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是一位严谨的编程评审老师。"),
                Map.of("role", "user", "content", prompt)));

        String url = config.baseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        StringBuilder fullContent = new StringBuilder();
        HttpResponse<java.io.InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("AI 接口返回 " + response.statusCode()
                    + "：" + abbreviate(errBody));
        }

        // 解析 SSE 流
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            fullContent.append(delta.asText());
                            onChunk.accept(fullContent.toString());
                        }
                    } catch (Exception ignored) {
                        // 跳过无法解析的行
                    }
                }
            }
        }
        return fullContent.toString();
    }

    // 各维度权重（与 prompt 中一致，6 维度）
    private static final Map<String, Double> DIMENSION_WEIGHTS = Map.ofEntries(
            Map.entry("algorithm_efficiency", 0.30),
            Map.entry("boundary_handling", 0.20),
            Map.entry("readability", 0.20),
            Map.entry("code_structure", 0.15),
            Map.entry("robustness", 0.10),
            Map.entry("best_practices", 0.05)
    );

    /** 解析 AI 输出 JSON，由维度加权计算质量分，组装 AiReview。 */
    private AiReview parseReview(String content, int passRate) throws Exception {
        String json = extractJsonObject(content);
        log.debug("AI 评审提取的 JSON：{}", abbreviate(json));
        JsonNode node = objectMapper.readTree(json);

        // 解析各维度分数
        Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        JsonNode scoresNode = node.get("dimensionScores");
        if (scoresNode != null && scoresNode.isObject()) {
            for (String dimId : DIMENSION_WEIGHTS.keySet()) {
                int s = clamp(scoresNode.path(dimId).asInt(0), 0, 100);
                dimensionScores.put(dimId, s);
            }
        }

        // 由维度加权计算 qualityScore
        double weighted = 0;
        for (Map.Entry<String, Integer> e : dimensionScores.entrySet()) {
            double w = DIMENSION_WEIGHTS.getOrDefault(e.getKey(), 0.0);
            weighted += e.getValue() * w;
        }
        int qualityScore = clamp((int) Math.round(weighted), 0, 100);

        List<String> feedback = new ArrayList<>();
        JsonNode feedbackNode = node.get("feedback");
        if (feedbackNode != null && feedbackNode.isArray()) {
            for (JsonNode item : feedbackNode) {
                if (item != null && item.isTextual()) {
                    feedback.add(item.asText());
                }
            }
        }

        String timeComplexity = node.path("timeComplexity").asText(null);
        String spaceComplexity = node.path("spaceComplexity").asText(null);
        String summary = node.path("summary").asText(null);

        // 兜底：AI 未返回复杂度字段时，尝试从 feedback 和 summary 中提取
        if (timeComplexity == null || spaceComplexity == null) {
            String allText = String.join(" ", feedback) + " " + nullToEmpty(summary);
            if (timeComplexity == null) {
                timeComplexity = extractComplexity(allText, "时间");
            }
            if (spaceComplexity == null) {
                spaceComplexity = extractComplexity(allText, "空间");
            }
            log.info("AI 评审复杂度兜底提取：timeComplexity={}, spaceComplexity={}", timeComplexity, spaceComplexity);
        }

        int score = (int) Math.round(passRate * 0.7 + qualityScore * 0.3);
        return new AiReview(score, passRate, qualityScore, feedback,
                timeComplexity, spaceComplexity, dimensionScores, summary);
    }

    /**
     * 从 AI 输出中截取最后一个完整的 JSON 对象。
     *
     * <p>容忍 Markdown 代码块包裹、thinking 块等前后杂文。
     * 使用「从后向前找匹配的 {」策略，确保取到的是真正的评审 JSON，
     * 而非 thinking 过程中产生的中间 JSON。</p>
     */
    private String extractJsonObject(String content) {
        String s = content.trim()
                .replaceAll("^```[a-zA-Z]*\\s*", "")
                .replaceAll("\\s*```$", "");
        // 从后向前扫描：找到最后一个 '}'，再向前匹配对应的 '{'
        int end = s.lastIndexOf('}');
        if (end < 0) {
            throw new IllegalStateException("AI 输出未包含 JSON 对象");
        }
        int depth = 0;
        int start = -1;
        for (int i = end; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') depth--;
            if (depth == 0) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            throw new IllegalStateException("AI 输出未包含完整 JSON 对象");
        }
        return s.substring(start, end + 1);
    }

    /**
     * 从文本中提取复杂度描述（如 "O(n)"、"O(log n)"）。
     *
     * <p>当 AI 未在 JSON 中返回 timeComplexity/spaceComplexity 时，
     * 从 feedback 和 summary 文本中兜底提取含 "时间/空间复杂度" 上下文的 O(...) 表达式。</p>
     *
     * @param text     待搜索的文本（feedback + summary 拼接）
     * @param keyword  "时间" 或 "空间"
     * @return 复杂度字符串；未找到返回 null
     */
    private String extractComplexity(String text, String keyword) {
        if (text == null || text.isBlank()) return null;
        // 匹配模式：keyword + 复杂度 + O(...)，如 "时间复杂度为 O(n)"、"空间复杂度 O(1)"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                keyword + "复杂度[^O]*?(O\\([^)]+\\))");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        // 兜底：文本中任意位置的 O(...) 表达式（仅当 keyword 相关上下文找不到时）
        // 不做兜底，避免误提取无关的 O(...)
        return null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 归一化语言名（供 AI 提示词引用），空值/未知回退 Java。 */
    private String languageName(String language) {
        if (language == null || language.isBlank()) {
            return "Java";
        }
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "c" -> "C";
            case "c++", "cpp" -> "C++";
            case "python", "python3", "py" -> "Python";
            case "go", "golang" -> "Go";
            default -> "Java";
        };
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