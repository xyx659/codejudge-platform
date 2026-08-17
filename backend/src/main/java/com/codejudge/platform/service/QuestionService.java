package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExternalQuestionCandidate;
import com.codejudge.platform.dto.QuestionImportError;
import com.codejudge.platform.dto.QuestionImportResult;
import com.codejudge.platform.dto.QuestionManageDetail;
import com.codejudge.platform.dto.QuestionManageSummary;
import com.codejudge.platform.dto.QuestionSaveRequest;
import com.codejudge.platform.dto.QuestionTestCaseRequest;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 管理端题库服务，负责题目 CRUD、测试用例维护和 JSON 模板导入。
 */
@Service
public class QuestionService {

    private static final long MAX_TEMPLATE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> DIFFICULTIES = Set.of("简单", "中等", "困难");
    private static final int MAX_TEST_CASES = 100;

    private final MongoTemplate mongoTemplate;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    public QuestionService(
            MongoTemplate mongoTemplate,
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository,
            ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.objectMapper = objectMapper;
    }

    public PageResult<QuestionManageSummary> listQuestions(
            int page,
            int size,
            String keyword,
            String difficulty,
            String tag,
            Boolean published) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Criteria criteria = new Criteria();

        if (keyword != null && !keyword.isBlank()) {
            criteria.and("title").regex(
                    Pattern.quote(keyword.trim()),
                    "i");
        }
        if (difficulty != null && !difficulty.isBlank()) {
            criteria.and("difficulty").is(difficulty.trim());
        }
        if (tag != null && !tag.isBlank()) {
            criteria.and("tags").in(tag.trim());
        }
        if (published != null) {
            criteria.and("published").is(published);
        }

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, Question.class);
        List<Question> questions = mongoTemplate.find(
                query.with(PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createdAt"))),
                Question.class);
        return new PageResult<>(
                questions.stream().map(QuestionManageSummary::from).toList(),
                safePage,
                safeSize,
                total);
    }

    public QuestionManageDetail getQuestion(String id) {
        return QuestionManageDetail.from(requireQuestion(id));
    }

    public QuestionManageDetail createManual(QuestionSaveRequest request) {
        Question question = new Question();
        applyRequest(question, request);
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public QuestionManageDetail updateQuestion(
            String id,
            QuestionSaveRequest request) {
        Question question = requireQuestion(id);
        applyRequest(question, request);
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public QuestionManageDetail addTestCase(
            String id,
            QuestionTestCaseRequest request) {
        Question question = requireQuestion(id);
        List<QuestionTestCase> testCases =
                new ArrayList<>(question.getTestCases());
        testCases.add(cleanTestCase(request));
        if (testCases.size() > MAX_TEST_CASES) {
            throw new BadRequestException("测试用例数量不能超过 " + MAX_TEST_CASES);
        }
        question.setTestCases(testCases);
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public QuestionManageDetail updateTestCase(
            String id,
            int index,
            QuestionTestCaseRequest request) {
        Question question = requireQuestion(id);
        List<QuestionTestCase> testCases =
                new ArrayList<>(question.getTestCases());
        if (index < 0 || index >= testCases.size()) {
            throw new BadRequestException("测试用例序号不存在");
        }
        testCases.set(index, cleanTestCase(request));
        question.setTestCases(testCases);
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public QuestionManageDetail deleteTestCase(String id, int index) {
        Question question = requireQuestion(id);
        List<QuestionTestCase> testCases =
                new ArrayList<>(question.getTestCases());
        if (index < 0 || index >= testCases.size()) {
            throw new BadRequestException("测试用例序号不存在");
        }
        testCases.remove(index);
        question.setTestCases(testCases);
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public QuestionManageDetail setPublished(String id, boolean published) {
        Question question = requireQuestion(id);
        question.setPublished(published);
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public void deleteQuestion(String id) {
        Question question = requireQuestion(id);
        if (submissionRepository.existsByQuestionId(question.getId())) {
            throw new BadRequestException("该题目已有学生提交记录，不能删除");
        }
        questionRepository.delete(question);
    }

    public QuestionManageDetail importExternalCandidate(
            ExternalQuestionCandidate candidate) {
        if (candidate == null
                || candidate.sourcePlatform() == null
                || candidate.sourceId() == null) {
            throw new BadRequestException("外部题目信息不完整");
        }
        questionRepository.findBySourcePlatformAndSourceId(
                        candidate.sourcePlatform(),
                        candidate.sourceId())
                .ifPresent(existing -> {
                    throw new BadRequestException("该题目已导入");
                });

        Question question = new Question(
                required(candidate.title(), "题目标题不能为空"),
                cleanText(candidate.description()),
                cleanText(candidate.methodName()));
        question.setLanguage(normalizeLanguage(candidate.language()));
        question.setDifficulty(requireDifficulty(candidate.difficulty()));
        question.setTags(cleanTags(candidate.tags()));
        List<QuestionTestCase> testCases = new ArrayList<>();
        if (candidate.testCases() != null) {
            for (QuestionTestCaseRequest item : candidate.testCases()) {
                testCases.add(cleanTestCase(item));
            }
        }
        if (testCases.size() > MAX_TEST_CASES) {
            throw new BadRequestException("测试用例数量不能超过 " + MAX_TEST_CASES);
        }
        question.setTestCases(testCases);
        question.setPublished(false);
        question.setSourcePlatform(candidate.sourcePlatform());
        question.setSourceId(candidate.sourceId());
        question.setSourceUrl(candidate.sourceUrl());
        question.setSourceMetadata(candidate.sourceMetadata());
        return QuestionManageDetail.from(questionRepository.save(question));
    }

    public QuestionImportResult importTemplate(MultipartFile file) {
        validateTemplateFile(file);
        try {
            JsonNode root = objectMapper.readTree(file.getInputStream());
            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray()) {
                throw new BadRequestException("JSON 模板必须包含 questions 数组");
            }

            int total = questionsNode.size();
            int successCount = 0;
            List<QuestionImportError> errors = new ArrayList<>();
            for (int i = 0; i < questionsNode.size(); i++) {
                JsonNode node = questionsNode.get(i);
                String title = node == null ? null : text(node, "title");
                try {
                    if (node == null || !node.isObject()) {
                        throw new BadRequestException("题目数据格式错误");
                    }
                    createManual(parseTemplateQuestion(node));
                    successCount++;
                } catch (BadRequestException e) {
                    errors.add(new QuestionImportError(
                            i + 1,
                            title,
                            e.getMessage()));
                }
            }
            return new QuestionImportResult(
                    total,
                    successCount,
                    errors.size(),
                    List.copyOf(errors));
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("JSON 模板读取失败");
        }
    }

    private QuestionSaveRequest parseTemplateQuestion(JsonNode node) {
        return new QuestionSaveRequest(
                required(text(node, "title"), "题目标题不能为空"),
                text(node, "description"),
                required(text(node, "methodName"), "方法名不能为空"),
                required(text(node, "language"), "编程语言不能为空"),
                required(text(node, "difficulty"), "难度不能为空"),
                stringList(node.get("tags")),
                node.hasNonNull("published")
                        ? node.get("published").asBoolean()
                        : false,
                parseTestCases(node.get("testCases")));
    }

    private List<QuestionTestCaseRequest> parseTestCases(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new BadRequestException("测试用例必须是数组");
        }
        List<QuestionTestCaseRequest> testCases = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                throw new BadRequestException("测试用例格式错误");
            }
            testCases.add(new QuestionTestCaseRequest(
                    required(text(item, "name"), "测试用例名称不能为空"),
                    required(text(item, "input"), "测试用例输入不能为空"),
                    required(text(item, "expected"), "测试用例期望输出不能为空")));
        }
        return testCases;
    }

    private void applyRequest(Question question, QuestionSaveRequest request) {
        if (request == null) {
            throw new BadRequestException("题目数据不能为空");
        }
        question.setTitle(required(request.title(), "题目标题不能为空"));
        question.setDescription(cleanText(request.description()));
        question.setMethodName(required(request.methodName(), "方法名不能为空"));
        question.setLanguage(normalizeLanguage(request.language()));
        question.setDifficulty(requireDifficulty(request.difficulty()));
        question.setTags(cleanTags(request.tags()));
        question.setPublished(request.published() == null
                ? false
                : request.published());

        List<QuestionTestCase> testCases = new ArrayList<>();
        if (request.testCases() != null) {
            for (QuestionTestCaseRequest item : request.testCases()) {
                testCases.add(cleanTestCase(item));
            }
        }
        if (testCases.size() > MAX_TEST_CASES) {
            throw new BadRequestException("测试用例数量不能超过 " + MAX_TEST_CASES);
        }
        question.setTestCases(testCases);
    }

    private QuestionTestCase cleanTestCase(QuestionTestCaseRequest request) {
        if (request == null) {
            throw new BadRequestException("测试用例不能为空");
        }
        String name = required(request.name(), "测试用例名称不能为空");
        String input = required(request.input(), "测试用例输入不能为空");
        String expected = required(request.expected(), "测试用例期望输出不能为空");
        if (name.length() > 100
                || input.length() > 10000
                || expected.length() > 10000) {
            throw new BadRequestException("测试用例字段长度超出限制");
        }
        return new QuestionTestCase(name, input, expected);
    }

    private List<String> cleanTags(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String tag : source) {
            String value = cleanText(tag);
            if (value != null) {
                tags.add(value);
            }
        }
        if (tags.size() > 20) {
            throw new BadRequestException("标签数量不能超过 20 个");
        }
        return List.copyOf(tags);
    }

    private String normalizeLanguage(String language) {
        String value = required(language, "编程语言不能为空")
                .toLowerCase(Locale.ROOT);
        if (!"java".equals(value)) {
            throw new BadRequestException("第一版仅支持 Java 题目");
        }
        return "Java";
    }

    private String requireDifficulty(String difficulty) {
        String value = required(difficulty, "难度不能为空");
        if (!DIFFICULTIES.contains(value)) {
            throw new BadRequestException("难度只能是：简单、中等、困难");
        }
        return value;
    }

    private Question requireQuestion(String id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("题目不存在"));
    }

    private void validateTemplateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择 JSON 模板文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new BadRequestException("仅支持上传 JSON 模板");
        }
        if (file.getSize() > MAX_TEMPLATE_SIZE) {
            throw new BadRequestException("JSON 模板大小不能超过 5MB");
        }
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item == null ? null : item.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        });
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String required(String value, String message) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            throw new BadRequestException(message);
        }
        return cleaned;
    }
}
