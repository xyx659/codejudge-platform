package com.codejudge.platform.service;

import com.codejudge.platform.dto.DashboardStats;
import com.codejudge.platform.dto.ExamSummary;
import com.codejudge.platform.entity.Category;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.TeacherQuestion;
import com.codejudge.platform.repository.CategoryRepository;
import com.codejudge.platform.repository.ExamRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师工作台统计逻辑。
 *
 * <p>汇总题库、考试、学生、提交等多维度数量，并给出分类分布与最近考试，
 * 供工作台首页展示。</p>
 */
@Service
public class DashboardService {

    private final MongoTemplate mongoTemplate;
    private final CategoryRepository categoryRepository;
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final SubmissionRepository submissionRepository;

    public DashboardService(MongoTemplate mongoTemplate,
                            CategoryRepository categoryRepository,
                            ExamRepository examRepository,
                            StudentRepository studentRepository,
                            SubmissionRepository submissionRepository) {
        this.mongoTemplate = mongoTemplate;
        this.categoryRepository = categoryRepository;
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionRepository = submissionRepository;
    }

    /** 汇总工作台各项统计 */
    public DashboardStats stats() {
        // 题库统计（questions 集合里既有团队种子题，也有教师新增题，统一按集合计数）
        long questionCount = mongoTemplate.count(new Query(), Question.class);
        long publishedQuestionCount = mongoTemplate.count(
                new Query(Criteria.where("published").is(true)), Question.class);

        // 考试统计
        long examCount = examRepository.count();
        long publishedExamCount = mongoTemplate.count(
                new Query(Criteria.where("status").is("PUBLISHED")), Exam.class);

        // 用户与提交统计
        long studentCount = studentRepository.count();
        long submissionCount = submissionRepository.count();

        // 各分类下的题目数（保持分类的排序号顺序）
        Map<String, Long> categoryDistribution = new LinkedHashMap<String, Long>();
        for (Category category : categoryRepository.findAllByOrderBySortOrderAsc()) {
            long count = mongoTemplate.count(
                    new Query(Criteria.where("categoryId").is(category.getId())), TeacherQuestion.class);
            categoryDistribution.put(category.getName(), count);
        }

        // 最近创建的 5 场考试
        Query recentQuery = new Query().with(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ExamSummary> recentExams = mongoTemplate.find(recentQuery, Exam.class)
                .stream()
                .map(ExamSummary::from)
                .toList();

        return new DashboardStats(
                questionCount, publishedQuestionCount,
                examCount, publishedExamCount,
                studentCount, submissionCount,
                categoryDistribution, recentExams);
    }
}
