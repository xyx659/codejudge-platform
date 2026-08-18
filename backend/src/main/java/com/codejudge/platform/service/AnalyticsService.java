package com.codejudge.platform.service;

import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.AbilityItem;
import com.codejudge.platform.dto.ExamAnalytics;
import com.codejudge.platform.dto.ScoreBucket;
import com.codejudge.platform.dto.ScoreStats;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.ExamQuestion;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.repository.ExamRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionQueryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情分析业务逻辑。
 *
 * <p>对一场考试做三类分析：成绩统计、分数段分布、逐题掌握度。
 * 分数计算规则与监考模块一致：每题取最佳得分并封顶为该题分值，再累加。</p>
 */
@Service
public class AnalyticsService {

    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final SubmissionQueryRepository submissionQueryRepository;

    public AnalyticsService(ExamRepository examRepository,
                            StudentRepository studentRepository,
                            SubmissionQueryRepository submissionQueryRepository) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionQueryRepository = submissionQueryRepository;
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

    /** 保留一位小数 */
    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
