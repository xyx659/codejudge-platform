package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.AlertItem;
import com.codejudge.platform.dto.MonitorStudentStatus;
import com.codejudge.platform.dto.MonitorSummary;
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
 * 考试监考业务逻辑。
 *
 * <p>监考页每 5 秒轮询一次，拉取一场考试的整体进度、每个学生的作答状态与预警。
 * 这里基于「学生对组卷题目的历史提交」来推断答题情况，属于 MVP 简化实现。</p>
 *
 * <p>说明：团队的提交记录（MySQL submissions 表）没有「考试 ID」字段，
 * 因此本模块通过「提交记录里的题目 ID 是否属于本场考试」来关联，
 * 并按「每题取最佳得分、封顶为该题分值」累计学生总分。</p>
 */
@Service
public class MonitorService {

    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final SubmissionQueryRepository submissionQueryRepository;

    public MonitorService(ExamRepository examRepository,
                          StudentRepository studentRepository,
                          SubmissionQueryRepository submissionQueryRepository) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionQueryRepository = submissionQueryRepository;
    }

    /** 查询一场考试的监考总览 */
    public MonitorSummary summary(String examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        // 草稿状态还没有学生参加，无需监考
        if ("DRAFT".equals(exam.getStatus())) {
            throw new BadRequestException("考试尚未发布，无法监考");
        }

        List<ExamQuestion> questions = exam.getQuestions();
        List<Student> students = studentRepository.findAll();
        // 学生 -> （题目 -> 最佳得分）
        Map<Long, Map<String, Integer>> bestByStudent = computeBestScores(questions);

        List<MonitorStudentStatus> statusList = new ArrayList<MonitorStudentStatus>();
        List<AlertItem> alerts = new ArrayList<AlertItem>();
        int submittedCount = 0;
        double scoreSum = 0.0;

        for (Student student : students) {
            Map<String, Integer> best = bestByStudent.getOrDefault(student.getId(), Map.of());
            int submitted = (int) questions.stream()
                    .filter(q -> best.containsKey(q.getQuestionId()))
                    .count();
            int score = totalScore(questions, best);

            String statusText;
            if (submitted == 0) {
                statusText = "未开始";
            } else if (submitted < questions.size()) {
                statusText = "答题中";
            } else {
                statusText = "已交卷";
            }

            if (submitted > 0) {
                submittedCount++;
                scoreSum += score;
            }

            statusList.add(new MonitorStudentStatus(
                    student.getId(), student.getStudentNo(), student.getName(),
                    submitted, questions.size(), score, statusText));

            // 预警一：考试进行中却还没开始作答
            if (submitted == 0 && "PUBLISHED".equals(exam.getStatus())) {
                alerts.add(new AlertItem(student.getId(), student.getName(),
                        "未开始", "考试进行中，尚未开始作答"));
            }
            // 预警二：作答过但存在 0 分的题目
            boolean hasZero = best.values().stream().anyMatch(v -> v <= 0);
            if (submitted > 0 && hasZero) {
                alerts.add(new AlertItem(student.getId(), student.getName(),
                        "零分题", "存在得 0 分的题目，请关注"));
            }
        }

        double avgScore = submittedCount == 0 ? 0.0 : round1(scoreSum / submittedCount);

        return new MonitorSummary(
                exam.getId(), exam.getTitle(), exam.getStatus(),
                students.size(), submittedCount, avgScore, statusList, alerts);
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
