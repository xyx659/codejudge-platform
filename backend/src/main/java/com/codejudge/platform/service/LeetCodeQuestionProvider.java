package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.ExternalQuestionCandidate;
import com.codejudge.platform.dto.QuestionTestCaseRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LeetCode GraphQL 题目 Provider。
 */
@Service
public class LeetCodeQuestionProvider implements ExternalQuestionProvider {

    private static final Logger log = LoggerFactory.getLogger(
            LeetCodeQuestionProvider.class);
    private static final String GRAPHQL_URL = "https://leetcode.cn/graphql/";
    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "<strong>\\s*输入\\s*[:：]\\s*</strong>\\s*(.*?)(?=<strong>|</pre>|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OUTPUT_PATTERN = Pattern.compile(
            "<strong>\\s*输出\\s*[:：]\\s*</strong>\\s*(.*?)(?=<strong>|</pre>|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String QUERY = """
            query questionData($titleSlug: String!) {
              question(titleSlug: $titleSlug) {
                questionId
                title
                translatedTitle
                translatedContent
                titleSlug
                content
                difficulty
                topicTags { name translatedName }
                codeSnippets { lang langSlug code }
                metaData
                exampleTestcaseList
              }
            }
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LeetCodeQuestionProvider(
            RestTemplate externalQuestionRestTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = externalQuestionRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String platform() {
        return "LEETCODE_CN";
    }

    @Override
    public List<ExternalQuestionCandidate> search(
            String keyword,
            String difficulty,
            int page,
            int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        ExternalQuestionCandidate candidate = getDetail(keyword.trim());
        if (difficulty != null
                && !difficulty.isBlank()
                && !difficulty.equals(candidate.difficulty())) {
            return List.of();
        }
        return page <= 0 ? List.of(candidate) : List.of();
    }

    @Override
    public ExternalQuestionCandidate getDetail(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new BadRequestException("LeetCode titleSlug 不能为空");
        }
        String slug = sourceId.trim();
        try {
            Map<String, Object> body = Map.of(
                    "query", QUERY,
                    "variables", Map.of("titleSlug", slug));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "codejudge-platform/1.0");
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(
                    GRAPHQL_URL, request, String.class);
            if (response == null) {
                throw new BadRequestException("LeetCode API 返回为空");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode question = root.at("/data/question");
            if (question == null || question.isNull() || question.isMissingNode()) {
                throw new BadRequestException("未找到 LeetCode 题目：" + slug);
            }

            String title = firstText(
                    question,
                    "translatedTitle",
                    "title");
            String originalDifficulty = text(question, "difficulty");
            String difficulty = mapDifficulty(originalDifficulty);
            String rawContent = firstText(
                    question,
                    "translatedContent",
                    "content");
            String description = cleanHtml(rawContent);
            String methodName = parseMethodName(question.get("metaData"));
            List<String> tags = topicTags(question.get("topicTags"));
            List<QuestionTestCaseRequest> testCases =
                    parseTestCases(rawContent);
            String questionId = text(question, "questionId");
            String sourceUrl = "https://leetcode.cn/problems/" + slug + "/";

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("questionId", questionId);
            metadata.put("titleSlug", slug);
            metadata.put("originalDifficulty", originalDifficulty);
            metadata.put("exampleTestcaseList",
                    text(question, "exampleTestcaseList"));

            return new ExternalQuestionCandidate(
                    platform(),
                    slug,
                    title,
                    description,
                    methodName,
                    "Java",
                    difficulty,
                    tags,
                    testCases,
                    sourceUrl,
                    metadata);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LeetCode GraphQL 请求失败：slug={}", slug, e);
            throw new BadRequestException("LeetCode 题目获取失败，请稍后重试");
        }
    }

    private String parseMethodName(JsonNode metaDataNode) {
        if (metaDataNode == null || metaDataNode.isNull()) {
            return null;
        }
        try {
            JsonNode metaData = objectMapper.readTree(metaDataNode.asText());
            return text(metaData, "name");
        } catch (Exception e) {
            return null;
        }
    }

    private String mapDifficulty(String value) {
        if (value == null) {
            return "简单";
        }
        return switch (value.toUpperCase()) {
            case "MEDIUM" -> "中等";
            case "HARD" -> "困难";
            default -> "简单";
        };
    }

    private List<String> topicTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        tagsNode.forEach(item -> {
            String name = firstText(item, "translatedName", "name");
            if (name != null && !name.isBlank()) {
                tags.add(name);
            }
        });
        return tags;
    }

    private List<QuestionTestCaseRequest> parseTestCases(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<String> inputs = extractMatches(INPUT_PATTERN, html);
        List<String> outputs = extractMatches(OUTPUT_PATTERN, html);
        int size = Math.min(inputs.size(), outputs.size());
        if (size == 0) {
            return List.of();
        }
        List<QuestionTestCaseRequest> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String input = plainTextPreserveLines(inputs.get(i));
            String expected = plainTextPreserveLines(outputs.get(i));
            if (input == null || expected == null) {
                continue;
            }
            result.add(new QuestionTestCaseRequest(
                    "样例" + (i + 1),
                    input,
                    expected));
        }
        return result.size() > 20 ? result.subList(0, 20) : result;
    }

    private List<String> extractMatches(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String plainTextPreserveLines(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</pre>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("\r", "")
                .trim();
        cleaned = HtmlUtils.htmlUnescape(cleaned);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String firstText(JsonNode node, String firstField, String secondField) {
        String value = text(node, firstField);
        return value == null ? text(node, secondField) : value;
    }

    private String cleanHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
        return HtmlUtils.htmlUnescape(cleaned);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }
}
