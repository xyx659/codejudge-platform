package com.codejudge.platform.test;

import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.QuestionTestCase;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.service.JudgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 多语言判题冒烟测试：读题库 JSON → 导入 MongoDB → 逐题逐语言提交参考解 → 走真实判题引擎 → 汇总。
 *
 * <p>由 {@link MultilangJudgeTest#main} 启动后调用；本类不做 Spring Bean，避免污染正常应用启动。</p>
 *
 * <p>参考解落地约定见 {@code solutions/MANIFEST.md}：C 放 {@code solutions/c/01.c~47.c}，
 * C++ 放 {@code solutions/cpp/01.cpp~47.cpp}。</p>
 */
public class MultilangTestRunner {

    private static final long POLL_INTERVAL_MS = 300L;
    private static final long JUDGE_TIMEOUT_MS = 60_000L;

    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionDetailRepository submissionDetailRepository;
    private final StudentRepository studentRepository;
    private final JudgeService judgeService;
    private final ObjectMapper objectMapper;

    public MultilangTestRunner(QuestionRepository questionRepository,
                               SubmissionRepository submissionRepository,
                               SubmissionDetailRepository submissionDetailRepository,
                               StudentRepository studentRepository,
                               JudgeService judgeService,
                               ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.studentRepository = studentRepository;
        this.judgeService = judgeService;
        this.objectMapper = objectMapper;
    }

    /** 一行测试结果。 */
    private record Row(int no, String title, String language, boolean passed,
                       String status, Integer score, String message) {
    }

    public void run(String jsonPath, String solutionsDir) throws Exception {
        JsonNode questions = objectMapper.readTree(Path.of(jsonPath).toFile()).path("questions");
        Long studentId = ensureStudent();

        List<Row> rows = new ArrayList<>();
        int total = 0, passed = 0, failed = 0, skipped = 0;

        for (int i = 0; i < questions.size(); i++) {
            JsonNode qn = questions.get(i);
            int no = i + 1;
            String title = qn.path("title").asText("");
            for (String lang : List.of("C", "C++")) {
                total++;
                String dir = lang.equals("C") ? "c" : "cpp";
                String ext = lang.equals("C") ? "c" : "cpp";
                Path sol = Path.of(solutionsDir, dir, String.format("%02d.%s", no, ext));

                if (!Files.exists(sol)) {
                    skipped++;
                    rows.add(new Row(no, title, lang, false, "SKIP", null, "无参考解"));
                    System.out.printf("SKIP  %02d %s（%s）无参考解%n", no, title, lang);
                    continue;
                }

                String code = Files.readString(sol);
                Row row;
                try {
                    row = runOne(no, qn, lang, code, studentId);
                } catch (Exception e) {
                    row = new Row(no, title, lang, false, "EXCEPTION", null, e.getMessage());
                }
                rows.add(row);
                if (row.passed) {
                    passed++;
                } else {
                    failed++;
                }
                System.out.printf("%-5s %02d %s（%s）%s%s%n",
                        row.passed ? "PASS" : "FAIL", no, title, lang,
                        row.score == null ? "" : "score=" + row.score + " ",
                        row.message == null ? "" : "→ " + row.message);
            }
        }

        // 汇总
        System.out.println();
        System.out.println("========== 汇总 ==========");
        System.out.printf("总计 %d，通过 %d，失败 %d，跳过 %d%n", total, passed, failed, skipped);
        if (failed > 0) {
            System.out.println("失败明细：");
            for (Row r : rows) {
                if (!r.passed && !"SKIP".equals(r.status)) {
                    System.out.printf("  %02d %s（%s）%s：%s%n",
                            r.no, r.title, r.language, r.status, r.message);
                }
            }
        }
    }

    /** 导入一道题、提交参考解、触发判题并轮询，返回结果行。 */
    private Row runOne(int no, JsonNode qn, String lang, String code, Long studentId) throws Exception {
        Question q = buildQuestion(qn);
        q = questionRepository.save(q);

        Submission sub = new Submission(q.getId(), studentId);
        sub.setJudgeStatus("PENDING");
        sub.setLanguage(lang);
        sub = submissionRepository.save(sub);

        SubmissionDetail detail = new SubmissionDetail();
        detail.setSubmissionId(sub.getId());
        detail.setStudentId(studentId);
        detail.setQuestionId(q.getId());
        detail.setSourceCode(code);
        detail.setJudgeStatus("PENDING");
        submissionDetailRepository.save(detail);

        judgeService.trigger(sub.getId());
        waitForJudge(sub.getId());

        Submission done = submissionRepository.findById(sub.getId()).orElse(null);
        SubmissionDetail doneDetail = submissionDetailRepository
                .findBySubmissionIdAndStudentId(sub.getId(), studentId).orElse(null);

        String status = done == null || done.getJudgeStatus() == null ? "PENDING" : done.getJudgeStatus();
        Integer score = done == null ? null : done.getScore();
        boolean passed = score != null && score == 100;

        return new Row(no, qn.path("title").asText(""), lang, passed, status, score,
                passed ? null : buildMessage(doneDetail));
    }

    /** 从失败用例中拼出提示信息（最多 3 条）。 */
    private String buildMessage(SubmissionDetail detail) {
        if (detail == null || detail.getTestResults() == null) {
            return null;
        }
        List<String> fails = detail.getTestResults().stream()
                .filter(t -> !t.isPassed())
                .map(t -> t.getTestCaseName() + ": " + t.getMessage())
                .limit(3)
                .toList();
        return fails.isEmpty() ? null : String.join(" | ", fails);
    }

    /** 轮询直到判题状态离开 PENDING 或超时。 */
    private void waitForJudge(Long submissionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + JUDGE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Submission s = submissionRepository.findById(submissionId).orElse(null);
            if (s != null && s.getJudgeStatus() != null && !"PENDING".equals(s.getJudgeStatus())) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    /** 取测试学生（复用 DataInitializer 的 test 账号；不存在则创建）。 */
    private Long ensureStudent() {
        return studentRepository.findByUsername("test")
                .map(Student::getId)
                .orElseGet(() -> {
                    Student s = new Student("test", "判题测试", "x");
                    studentRepository.save(s);
                    return s.getId();
                });
    }

    /** JSON 题目节点 → Question 实体。 */
    private Question buildQuestion(JsonNode n) {
        Question q = new Question();
        q.setTitle(n.path("title").asText(""));
        q.setDescription(n.path("description").asText(""));
        q.setMethodName(n.path("methodName").asText(""));
        q.setJudgeMode(n.path("judgeMode").asText("METHOD"));
        q.setMethodSignature(n.hasNonNull("methodSignature") ? n.get("methodSignature").asText() : null);
        q.setDesignMethods(parseStringList(n.get("designMethods")));
        q.setDifficulty(n.path("difficulty").asText(""));
        q.setTags(parseStringList(n.get("tags")));
        q.setPublished(n.path("published").asBoolean(false));
        q.setTestCases(parseTestCases(n.get("testCases")));
        return q;
    }

    private List<String> parseStringList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode e : n) {
                out.add(e.asText());
            }
        }
        return out;
    }

    private List<QuestionTestCase> parseTestCases(JsonNode n) {
        List<QuestionTestCase> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode e : n) {
                out.add(new QuestionTestCase(e.path("name").asText(""),
                        e.path("input").asText(""), e.path("expected").asText("")));
            }
        }
        return out;
    }
}
