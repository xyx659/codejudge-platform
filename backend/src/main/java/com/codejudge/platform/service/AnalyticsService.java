package com.codejudge.platform.service;

import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.AbilityItem;
import com.codejudge.platform.dto.ExamAnalytics;
import com.codejudge.platform.dto.ScoreBucket;
import com.codejudge.platform.dto.ScoreStats;
import com.codejudge.platform.dto.StudentExamAnswer;
import com.codejudge.platform.dto.StudentScoreItem;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.ExamQuestion;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.repository.ExamRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionQueryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情分析业务逻辑。
 *
 * <p>对一场考试做三类分析：成绩统计、分数段分布、逐题掌握度；
 * 同时支持「学生成绩明细」（逐人得分分页）与「学生答卷」（逐题源码/评测/AI 评审）。
 * 分数计算规则与监考模块一致：每题取最佳得分并封顶为该题分值，再累加。</p>
 */
@Service
public class AnalyticsService {

    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final SubmissionQueryRepository submissionQueryRepository;
    private final SubmissionDetailRepository submissionDetailRepository;

    public AnalyticsService(ExamRepository examRepository,
                            StudentRepository studentRepository,
                            SubmissionQueryRepository submissionQueryRepository,
                            SubmissionDetailRepository submissionDetailRepository) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionQueryRepository = submissionQueryRepository;
        this.submissionDetailRepository = submissionDetailRepository;
    }

    /** 分析一场考试，返回统计指标 + 分布 + 逐题掌握度 */
    public ExamAnalytics analyze(String examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));

        List<ExamQuestion> questions = exam.getQuestions();
        List<Student> students = studentRepository.findAll();
        Map<Long, Map<String, Integer>> bestByStudent = computeBestScores(questions);

        // 先算出每个「已作答」学生的总分
        List<Integer> scores = new ArrayList<Integer>();
        for (Student student : students) {
            Map<String, Integer> best = bestByStudent.getOrDefault(student.getId(), Map.of());
            int submitted = (int) questions.stream()
                    .filter(q -> best.containsKey(q.getQuestionId()))
                    .count();
            if (submitted > 0) {
                scores.add(totalScore(questions, best));
            }
        }

        // 1. 成绩统计
        int submittedCount = scores.size();
        double avg = scores.isEmpty() ? 0 : round1(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
        int max = scores.isEmpty() ? 0 : scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = scores.isEmpty() ? 0 : scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        int passScore = exam.getPassScore() == null ? 0 : exam.getPassScore();
        long passCount = scores.stream().filter(s -> s >= passScore).count();
        double passRate = scores.isEmpty() ? 0 : round1(passCount * 100.0 / scores.size());

        ScoreStats stats = new ScoreStats(
                students.size(), submittedCount, avg, max, min, passRate, passScore);

        // 2. 分数段分布
        List<ScoreBucket> distribution = buildDistribution(scores);

        // 3. 逐题掌握度
        List<AbilityItem> abilities = buildAbilities(questions, students, bestByStudent);

        return new ExamAnalytics(stats, distribution, abilities);
    }

    /**
     * 分页查询一场考试的学生成绩明细。
     *
     * <p>支持关键字（姓名 / 账号 / 学号）、班级、是否及格三类筛选；
     * 学生数量有限，故先组装全量结果再在内存里过滤、排序、分页。</p>
     */
    public PageResult<StudentScoreItem> listStudentScores(String examId, int page, int size,
                                                          String keyword, String className, Boolean passed) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));

        List<ExamQuestion> questions = exam.getQuestions();
        List<Student> students = studentRepository.findAll();
        Map<Long, Map<String, Integer>> bestByStudent = computeBestScores(questions);
        int fullScore = questions.stream().mapToInt(q -> q.getScore() == null ? 0 : q.getScore()).sum();
        Integer passScore = exam.getPassScore();

        String kw = blankToNull(keyword);
        String cls = blankToNull(className);
        List<StudentScoreItem> all = new ArrayList<StudentScoreItem>();
        for (Student student : students) {
            Map<String, Integer> best = bestByStudent.getOrDefault(student.getId(), Map.of());
            int submitted = (int) questions.stream()
                    .filter(q -> best.containsKey(q.getQuestionId()))
                    .count();
            boolean hasSubmitted = submitted > 0;
            int achieved = totalScore(questions, best);
            boolean isPassed = hasSubmitted && passScore != null && achieved >= passScore;

            if (kw != null && !contains(student.getName(), kw)
                    && !contains(student.getUsername(), kw)
                    && !contains(student.getStudentNo(), kw)) {
                continue;
            }
            if (cls != null && !cls.equals(student.getClassName())) {
                continue;
            }
            if (passed != null && passed.booleanValue() != isPassed) {
                continue;
            }

            all.add(new StudentScoreItem(
                    student.getId(), student.getName(), student.getUsername(),
                    student.getStudentNo(), student.getClassName(),
                    hasSubmitted, achieved, fullScore, passScore, isPassed));
        }

        // 已交卷的排前面、按得分倒序；未交卷的排最后
        all.sort((a, b) -> {
            if (a.submitted() != b.submitted()) {
                return a.submitted() ? -1 : 1;
            }
            return Integer.compare(b.achievedScore(), a.achievedScore());
        });

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResult<>(new ArrayList<StudentScoreItem>(all.subList(from, to)), safePage, safeSize, all.size());
    }

    /**
     * 查询某学生在一场考试里的逐题答卷（含源码、评测明细与 AI 评审）。
     *
     * <p>按组卷题目顺序逐题返回，每题取该生在该题的最佳提交作代表；
     * 整卷未交卷的题目以「未作答」占位。</p>
     */
    public List<StudentExamAnswer> studentAnswers(String examId, Long studentId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));

        List<ExamQuestion> questions = exam.getQuestions();
        if (questions.isEmpty()) {
            return List.of();
        }

        // 取该生在本场考试每题的最佳提交作代表
        Map<String, Submission> best = new HashMap<String, Submission>();
        for (Submission s : submissionQueryRepository.findByStudentIdAndExamId(studentId, examId)) {
            Submission cur = best.get(s.getQuestionId());
            if (cur == null || scoreOf(s) > scoreOf(cur)) {
                best.put(s.getQuestionId(), s);
            }
        }

        List<StudentExamAnswer> result = new ArrayList<StudentExamAnswer>();
        for (ExamQuestion q : questions) {
            String title = q.getTitle() == null ? "未知题目" : q.getTitle();
            int cap = q.getScore() == null ? 0 : q.getScore();
            Submission sub = best.get(q.getQuestionId());
            if (sub == null) {
                // 该生整卷未交卷，或此题无提交记录
                result.add(new StudentExamAnswer(null, q.getQuestionId(), title, cap,
                        "UNANSWERED", 0, null, null, null));
                continue;
            }
            SubmissionDetail detail = sub.getId() == null ? null
                    : submissionDetailRepository
                            .findBySubmissionIdAndStudentId(sub.getId(), studentId)
                            .orElse(null);
            result.add(new StudentExamAnswer(
                    sub.getId(), q.getQuestionId(), title, cap,
                    sub.getJudgeStatus(), sub.getScore(),
                    detail == null ? null : detail.getSourceCode(),
                    detail == null ? null : detail.getTestResults(),
                    detail == null ? null : detail.getAiReview()));
        }
        return result;
    }

    /** 按 5 个分数段统计人数 */
    private List<ScoreBucket> buildDistribution(List<Integer> scores) {
        int[][] ranges = {{0, 59}, {60, 69}, {70, 79}, {80, 89}, {90, 100}};
        List<ScoreBucket> buckets = new ArrayList<ScoreBucket>();
        for (int[] range : ranges) {
            long count = scores.stream()
                    .filter(s -> s >= range[0] && s <= range[1])
                    .count();
            buckets.add(new ScoreBucket(range[0] + "-" + range[1], (int) count));
        }
        return buckets;
    }

    /** 逐题计算平均得分与完成率 */
    private List<AbilityItem> buildAbilities(List<ExamQuestion> questions,
                                             List<Student> students,
                                             Map<Long, Map<String, Integer>> bestByStudent) {
        List<AbilityItem> items = new ArrayList<AbilityItem>();
        int totalStudents = students.size();
        for (ExamQuestion question : questions) {
            int cap = question.getScore() == null ? 0 : question.getScore();
            int attempted = 0;
            double sum = 0.0;
            for (Student student : students) {
                Map<String, Integer> best = bestByStudent.getOrDefault(student.getId(), Map.of());
                Integer score = best.get(question.getQuestionId());
                if (score != null) {
                    sum += Math.min(score, cap);
                    attempted++;
                }
            }
            double avgScore = attempted == 0 ? 0 : round1(sum / attempted);
            double completionRate = totalStudents == 0 ? 0 : round1(attempted * 100.0 / totalStudents);
            String title = question.getTitle() == null ? "未知题目" : question.getTitle();
            items.add(new AbilityItem(title, avgScore, cap, completionRate));
        }
        return items;
    }

    /** 计算每个学生在各题的最佳得分（只统计分数非空的提交，取最大值） */
    private Map<Long, Map<String, Integer>> computeBestScores(List<ExamQuestion> questions) {
        Map<Long, Map<String, Integer>> result = new HashMap<Long, Map<String, Integer>>();
        if (questions.isEmpty()) {
            return result;
        }
        List<String> questionIds = questions.stream().map(ExamQuestion::getQuestionId).toList();
        List<Submission> submissions = submissionQueryRepository.findByQuestionIdIn(questionIds);
        for (Submission submission : submissions) {
            if (submission.getScore() == null) {
                continue;
            }
            result.computeIfAbsent(submission.getStudentId(), k -> new HashMap<String, Integer>())
                    .merge(submission.getQuestionId(), submission.getScore(), Math::max);
        }
        return result;
    }

    /** 学生总分：每题取 min(最佳得分, 该题分值) 累加 */
    private int totalScore(List<ExamQuestion> questions, Map<String, Integer> best) {
        int total = 0;
        for (ExamQuestion question : questions) {
            Integer score = best.get(question.getQuestionId());
            if (score == null) {
                continue;
            }
            int cap = question.getScore() == null ? 0 : question.getScore();
            total += Math.min(score, cap);
        }
        return total;
    }

    /** 提交得分，未出分为 -1（保证有分的提交优先作为每题代表） */
    private int scoreOf(Submission s) {
        return s.getScore() == null ? -1 : s.getScore();
    }

    /** 忽略大小写判断字符串是否包含关键字 */
    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    /** 空白字符串转 null */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 保留一位小数 */
    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
