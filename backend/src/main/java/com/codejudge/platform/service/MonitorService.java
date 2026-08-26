package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.AlertItem;
import com.codejudge.platform.dto.MonitorStudentStatus;
import com.codejudge.platform.dto.MonitorSummary;
import com.codejudge.platform.entity.CheatEvent;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.ExamQuestion;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.repository.CheatEventRepository;
import com.codejudge.platform.repository.ExamRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionQueryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final CheatEventRepository cheatEventRepository;

    public MonitorService(ExamRepository examRepository,
                          StudentRepository studentRepository,
                          SubmissionQueryRepository submissionQueryRepository,
                          CheatEventRepository cheatEventRepository) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionQueryRepository = submissionQueryRepository;
        this.cheatEventRepository = cheatEventRepository;
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
        // 学生 -> 已作答题目集合 / 各题最佳得分
        SubmissionState state = computeSubmissionState(questions);
        // 学生 -> 作弊事件次数，int[]{切屏次数, 切页面次数}
        Map<Long, int[]> cheatCounts = computeCheatCounts(examId);

        List<MonitorStudentStatus> statusList = new ArrayList<MonitorStudentStatus>();
        List<AlertItem> alerts = new ArrayList<AlertItem>();
        int submittedCount = 0;
        double scoreSum = 0.0;

        for (Student student : students) {
            Set<String> submittedQuestions = state.submitted().getOrDefault(student.getId(), Set.of());
            Map<String, Integer> best = state.bestScores().getOrDefault(student.getId(), Map.of());
            int submitted = (int) questions.stream()
                    .filter(q -> submittedQuestions.contains(q.getQuestionId()))
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

            int[] cheat = cheatCounts.getOrDefault(student.getId(), new int[]{0, 0});
            int switchTabCount = cheat[0];
            int leavePageCount = cheat[1];

            statusList.add(new MonitorStudentStatus(
                    student.getId(), student.getStudentNo(), student.getName(),
                    submitted, questions.size(), score, statusText,
                    switchTabCount, leavePageCount));

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
            // 预警三/四：切屏 / 切页面
            if (switchTabCount > 0) {
                alerts.add(new AlertItem(student.getId(), student.getName(),
                        "切屏", "考试期间切屏 " + switchTabCount + " 次"));
            }
            if (leavePageCount > 0) {
                alerts.add(new AlertItem(student.getId(), student.getName(),
                        "切页面", "考试期间离开页面 " + leavePageCount + " 次"));
            }
        }

        double avgScore = submittedCount == 0 ? 0.0 : round1(scoreSum / submittedCount);

        return new MonitorSummary(
                exam.getId(), exam.getTitle(), exam.getStatus(),
                students.size(), submittedCount, avgScore, statusList, alerts);
    }

    /** 学生作答情况：已作答题目集合 + 各题最佳得分（仅统计已出分的提交） */
    private record SubmissionState(
            Map<Long, Set<String>> submitted,
            Map<Long, Map<String, Integer>> bestScores) {
    }

    /**
     * 汇总每个学生的作答情况。
     *
     * <p>「是否作答」只看 MySQL submissions 里有没有该学生对某题的提交记录，
     * 不看分数——评测引擎尚未接入时分数为 null，若只看分数会把已交卷误判成未开始。</p>
     */
    private SubmissionState computeSubmissionState(List<ExamQuestion> questions) {
        Map<Long, Set<String>> submitted = new HashMap<Long, Set<String>>();
        Map<Long, Map<String, Integer>> bestScores = new HashMap<Long, Map<String, Integer>>();
        if (questions.isEmpty()) {
            return new SubmissionState(submitted, bestScores);
        }
        List<String> questionIds = questions.stream().map(ExamQuestion::getQuestionId).toList();
        List<Submission> submissions = submissionQueryRepository.findByQuestionIdIn(questionIds);
        for (Submission submission : submissions) {
            // 只要有提交记录，就算「已作答」（用于判断未开始/答题中/已交卷）
            submitted.computeIfAbsent(submission.getStudentId(), k -> new HashSet<String>())
                    .add(submission.getQuestionId());
            // 分数只在评测完成后才有值；未出分（null）不参与计分、也不触发零分预警
            if (submission.getScore() == null) {
                continue;
            }
            bestScores.computeIfAbsent(submission.getStudentId(), k -> new HashMap<String, Integer>())
                    .merge(submission.getQuestionId(), submission.getScore(), Math::max);
        }
        return new SubmissionState(submitted, bestScores);
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

    /**
     * 汇总一场考试里每个学生的防作弊事件次数。
     *
     * <p>返回 {@code 学生ID -> int[]{切屏次数, 切页面次数}}。</p>
     */
    private Map<Long, int[]> computeCheatCounts(String examId) {
        Map<Long, int[]> counts = new HashMap<Long, int[]>();
        List<CheatEvent> events = cheatEventRepository.findByExamId(examId);
        for (CheatEvent event : events) {
            int[] arr = counts.computeIfAbsent(event.getStudentId(), k -> new int[]{0, 0});
            if ("LEAVE_PAGE".equals(event.getEventType())) {
                arr[1]++;
            } else {
                arr[0]++;
            }
        }
        return counts;
    }

    /** 保留一位小数 */
    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
