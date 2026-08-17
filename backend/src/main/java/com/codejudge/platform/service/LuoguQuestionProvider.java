package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.dto.ExternalQuestionCandidate;
import com.codejudge.platform.dto.QuestionTestCaseRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 洛谷中文题目 Provider。
 */
@Service
public class LuoguQuestionProvider implements ExternalQuestionProvider {

    private static final Logger log = LoggerFactory.getLogger(
            LuoguQuestionProvider.class);
    private static final Pattern LENTILLE_PATTERN = Pattern.compile(
            "<script\\s+id=\"lentille-context\"\\s+type=\"application/json\">"
                    + "(.*?)</script>",
            Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LuoguQuestionProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String platform() {
        return "LUOGU";
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
            throw new BadRequestException("洛谷题目编号不能为空");
        }
        String pid = sourceId.trim().toUpperCase(Locale.ROOT);
        if (!pid.startsWith("P") && !pid.startsWith("B")
                && !pid.startsWith("U")) {
            pid = "P" + pid;
        }

        try {
            URI uri = URI.create(
                    "https://www.luogu.com.cn/problem/"
                            + pid
                            + "?_contentOnly=1");
            JsonNode problem = parseProblemJson(fetchBody(uri));
            if (problem == null || problem.isNull() || problem.isMissingNode()) {
                throw new BadRequestException("未找到洛谷题目：" + pid);
            }

            String title = firstText(
                    problem,
                    "name",
                    "pid");
            if (title == null) {
                title = text(problem.get("content"), "name");
            }
            JsonNode content = problem.has("content")
                    ? problem.get("content")
                    : problem.get("contenu");
            String description = buildDescription(content);
            int difficultyValue = number(problem, "difficulty").intValue();
            String difficulty = mapDifficulty(difficultyValue);
            List<QuestionTestCaseRequest> testCases =
                    parseTestCases(problem.get("samples"));

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("pid", pid);
            metadata.put("difficulty", difficultyValue);
            metadata.put("type", text(problem, "type"));

            return new ExternalQuestionCandidate(
                    platform(),
                    pid,
                    title,
                    description,
                    null,
                    "Java",
                    difficulty,
                    List.of(),
                    testCases,
                    "https://www.luogu.com.cn/problem/" + pid,
                    metadata);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("洛谷题目获取失败：pid={}", pid, e);
            throw new BadRequestException("洛谷题目获取失败，请稍后重试");
        }
    }

    private JsonNode parseProblemJson(String body) throws Exception {
        String json = body;
        Matcher matcher = LENTILLE_PATTERN.matcher(body);
        if (matcher.find()) {
            json = matcher.group(1);
        }
        JsonNode root = objectMapper.readTree(json);
        if (root.has("data") && root.get("data").has("problem")) {
            return root.at("/data/problem");
        }
        if (root.has("currentData")
                && root.get("currentData").has("problem")) {
            return root.at("/currentData/problem");
        }
        return root.get("problem");
    }

    private String fetchBody(URI uri) throws Exception {
        HttpRequest baseRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.luogu.com.cn/")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                baseRequest,
                HttpResponse.BodyHandlers.ofString());
        String cookie = firstCookie(response);
        for (int i = 0; i < 3 && response.statusCode() != 200; i++) {
            if (response.statusCode() != 302 || cookie == null) {
                break;
            }
            HttpRequest retry = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.luogu.com.cn/")
                    .header("Cookie", cookie)
                    .GET()
                    .build();
            response = httpClient.send(
                    retry,
                    HttpResponse.BodyHandlers.ofString());
            String nextCookie = firstCookie(response);
            if (nextCookie != null) {
                cookie = nextCookie;
            }
        }
        if (response.statusCode() != 200) {
            throw new BadRequestException("洛谷题目请求失败");
        }
        return response.body();
    }

    private String firstCookie(HttpResponse<String> response) {
        List<String> cookies = response.headers().allValues("set-cookie");
        for (String cookie : cookies) {
            if (cookie != null && !cookie.isBlank()) {
                int end = cookie.indexOf(';');
                return end < 0 ? cookie.trim() : cookie.substring(0, end).trim();
            }
        }
        return null;
    }

    private String buildDescription(JsonNode content) {
        if (content == null || !content.isObject()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendText(builder, content.get("background"));
        appendText(builder, content.get("description"));
        appendSection(builder, "输入格式", content.get("formatI"));
        appendSection(builder, "输出格式", content.get("formatO"));
        return builder.isEmpty() ? null : builder.toString().trim();
    }

    private void appendSection(
            StringBuilder builder,
            String label,
            JsonNode value) {
        String text = value == null ? null : value.asText(null);
        if (text != null && !text.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(label).append("：\n").append(text);
        }
    }

    private void appendText(StringBuilder builder, JsonNode value) {
        String text = value == null ? null : value.asText(null);
        if (text != null && !text.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(text);
        }
    }

    private List<QuestionTestCaseRequest> parseTestCases(JsonNode samples) {
        if (samples == null || !samples.isArray()) {
            return List.of();
        }
        List<QuestionTestCaseRequest> result = new ArrayList<>();
        int index = 1;
        for (JsonNode sample : samples) {
            if (!sample.isArray() || sample.size() < 2) {
                continue;
            }
            String input = cleanSample(sample.get(0).asText(null));
            String expected = cleanSample(sample.get(1).asText(null));
            if (input == null || expected == null) {
                continue;
            }
            result.add(new QuestionTestCaseRequest(
                    "样例" + index,
                    input,
                    expected));
            index++;
            if (result.size() >= 20) {
                break;
            }
        }
        return result;
    }

    private String cleanSample(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\r", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String mapDifficulty(int value) {
        if (value <= 2) {
            return "简单";
        }
        if (value >= 6) {
            return "困难";
        }
        return "中等";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private String firstText(JsonNode node, String firstField, String secondField) {
        String value = text(node, firstField);
        return value == null ? text(node, secondField) : value;
    }

    private Number number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? 0 : value.numberValue();
    }
}
