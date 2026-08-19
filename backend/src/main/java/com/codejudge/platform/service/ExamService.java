package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExamQuestionItem;
import com.codejudge.platform.dto.ExamRequest;
import com.codejudge.platform.dto.ExamSummary;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.ExamQuestion;
import com.codejudge.platform.entity.TeacherQuestion;
import com.codejudge.platform.repository.CategoryRepository;
import com.codejudge.platform.repository.ExamRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 考试业务逻辑（组卷、发布、关闭）。
 *
 * <p>组卷时会把每道题的标题/难度<b>快照</b>进考试文档，
 * 这样发布后即使题目被修改，已发布考试的内容也不会变。</p>
 */
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;
    private final QuestionVisibilityIndex visibilityIndex;

    public ExamService(ExamRepository examRepository,
                       CategoryRepository categoryRepository,
                       MongoTemplate mongoTemplate,
                       QuestionVisibilityIndex visibilityIndex) {
        this.examRepository = examRepository;
        this.categoryRepository = categoryRepository;
        this.mongoTemplate = mongoTemplate;
        this.visibilityIndex = visibilityIndex;
    }

    /** 分页查询考试，支持按状态 / 分类筛选 */
    public PageResult<ExamSummary> list(int page, int size, String status, String categoryId) {
        List<Criteria> conditions = new ArrayList<Criteria>();
        if (status != null && !status.isBlank()) {
            conditions.add(Criteria.where("status").is(status));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            conditions.add(Criteria.where("categoryId").is(categoryId));
        }

        Query query = new Query();
        if (!conditions.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(conditions.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, Exam.class);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ExamSummary> items = mongoTemplate.find(query.with(pageable), Exam.class)
                .stream()
                .map(ExamSummary::from)
                .toList();

        return new PageResult<ExamSummary>(items, page, size, total);
    }

    /** 查询考试完整详情（含组卷题目明细，供编辑回显） */
    public Exam detail(String id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
    }

    /** 新建考试（初始状态为草稿 DRAFT） */
    public Exam create(ExamRequest request) {
        Exam exam = new Exam();
        apply(exam, request);
        exam.setStatus("DRAFT");
        return examRepository.save(exam);
    }

    /** 修改考试（重新组卷并刷新快照） */
    public Exam update(String id, ExamRequest request) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        apply(exam, request);
        Exam saved = examRepository.save(exam);
        // 若修改的是已发布考试，题目可能变了，需重算学生可见题目索引
        visibilityIndex.rebuild();
        return saved;
    }

    /** 删除考试 */
    public void delete(String id) {
        examRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        examRepository.deleteById(id);
        visibilityIndex.rebuild();
    }

    /** 发布考试：草稿 → 已发布 */
    public Exam publish(String id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        if (exam.getQuestions().isEmpty()) {
            throw new BadRequestException("请先组卷再发布");
        }
        exam.setStatus("PUBLISHED");
        exam.setUpdatedAt(LocalDateTime.now());
        Exam saved = examRepository.save(exam);
        // 发布后把试卷里的题目同步进学生可见索引（Redis）
        visibilityIndex.rebuild();
        return saved;
    }

    /** 关闭考试：已发布 → 已结束 */
    public Exam close(String id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        exam.setStatus("CLOSED");
        exam.setUpdatedAt(LocalDateTime.now());
        Exam saved = examRepository.save(exam);
        // 关闭后把这些题目移出学生可见索引（Redis）
        visibilityIndex.rebuild();
        return saved;
    }

    /** 校验并填写考试字段（新建/修改共用） */
    private void apply(Exam exam, ExamRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("考试标题不能为空");
        }
        String categoryId = blankToNull(request.categoryId());
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new BadRequestException("所选分类不存在");
        }

        exam.setTitle(request.title().trim());
        exam.setDescription(request.description());
        exam.setCategoryId(categoryId);
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setDurationMinutes(request.durationMinutes());
        exam.setPassScore(request.passScore());
        exam.setTargetClass(request.targetClass());
        exam.setQuestions(buildQuestions(request.questions()));
        exam.setUpdatedAt(LocalDateTime.now());
    }

    /** 把组卷请求里的「题目 ID + 分值」转成带快照的 {@link ExamQuestion} 列表 */
    private List<ExamQuestion> buildQuestions(List<ExamQuestionItem> items) {
        List<ExamQuestion> result = new ArrayList<ExamQuestion>();
        if (items == null) {
            return result;
        }
        for (ExamQuestionItem item : items) {
            if (item.questionId() == null || item.questionId().isBlank()) {
                throw new BadRequestException("组卷中存在空的题目 ID");
            }
            // 组卷时快照题目标题与难度
            TeacherQuestion question = mongoTemplate.findById(item.questionId(), TeacherQuestion.class);
            if (question == null) {
                throw new BadRequestException("组卷中包含不存在的题目");
            }
            int score = item.score() == null ? 0 : item.score();
            result.add(new ExamQuestion(question.getId(), score, question.getTitle(), question.getDifficulty()));
        }
        return result;
    }

    /** 空白字符串转 null */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
