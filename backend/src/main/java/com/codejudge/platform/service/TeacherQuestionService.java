package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.QuestionRequest;
import com.codejudge.platform.dto.TeacherQuestionDetail;
import com.codejudge.platform.dto.TeacherQuestionSummary;
import com.codejudge.platform.entity.TeacherQuestion;
import com.codejudge.platform.repository.CategoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 教师端题库业务逻辑。
 *
 * <p>教师端题库统一使用 {@link TeacherQuestion} 实体 + {@link MongoTemplate} 读写，
 * 以支持「分类」字段，同时不修改团队写定的 {@code Question} 实体。</p>
 */
@Service
public class TeacherQuestionService {

    private final MongoTemplate mongoTemplate;
    private final CategoryRepository categoryRepository;
    private final QuestionVisibilityIndex visibilityIndex;

    public TeacherQuestionService(
            MongoTemplate mongoTemplate,
            CategoryRepository categoryRepository,
            QuestionVisibilityIndex visibilityIndex) {
        this.mongoTemplate = mongoTemplate;
        this.categoryRepository = categoryRepository;
        this.visibilityIndex = visibilityIndex;
    }

    /**
     * 分页查询题目，支持关键字 / 难度 / 分类 / 标签筛选。
     *
     * <p>教师端能看到所有题目（含未发布草稿），与学生端只能看已发布不同。</p>
     */
    public PageResult<TeacherQuestionSummary> list(
            int page,
            int size,
            String keyword,
            String difficulty,
            String categoryId,
            String tag,
            Boolean published) {
        List<Criteria> conditions = new ArrayList<Criteria>();

        if (keyword != null && !keyword.isBlank()) {
            Pattern pattern = Pattern.compile(
                    Pattern.quote(keyword),
                    Pattern.CASE_INSENSITIVE);
            conditions.add(new Criteria().orOperator(
                    Criteria.where("title").regex(pattern),
                    Criteria.where("description").regex(pattern)));
        }
        if (difficulty != null && !difficulty.isBlank()) {
            conditions.add(Criteria.where("difficulty").is(difficulty));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            conditions.add(Criteria.where("categoryId").is(categoryId));
        }
        if (tag != null && !tag.isBlank()) {
            conditions.add(Criteria.where("tags").in(tag));
        }
        // 发布状态筛选（组卷时传 published=true，只让老师挑学生可见的已发布题目）
        if (published != null) {
            conditions.add(Criteria.where("published").is(published));
        }

        Query query = new Query();
        if (!conditions.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(
                    conditions.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, TeacherQuestion.class);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TeacherQuestionSummary> items = mongoTemplate
                .find(query.with(pageable), TeacherQuestion.class)
                .stream()
                .map(TeacherQuestionSummary::from)
                .toList();

        return new PageResult<TeacherQuestionSummary>(
                items,
                page,
                size,
                total);
    }

    /** 查询单个题目完整详情（供编辑表单回显） */
    public TeacherQuestionDetail detail(String id) {
        TeacherQuestion question = mongoTemplate.findById(
                id,
                TeacherQuestion.class);
        if (question == null) {
            throw new NotFoundException("题目不存在");
        }
        return TeacherQuestionDetail.from(question);
    }

    /** 新增题目 */
    public TeacherQuestionDetail create(QuestionRequest request) {
        TeacherQuestion question = new TeacherQuestion();
        apply(question, request);
        mongoTemplate.save(question);
        // 新增可能直接发布（published=true），重算学生可见索引
        visibilityIndex.rebuild();
        return TeacherQuestionDetail.from(question);
    }

    /** 修改题目（整体覆盖字段，创建时间保持不变） */
    public TeacherQuestionDetail update(
            String id,
            QuestionRequest request) {
        TeacherQuestion question = mongoTemplate.findById(
                id,
                TeacherQuestion.class);
        if (question == null) {
            throw new NotFoundException("题目不存在");
        }
        apply(question, request);
        mongoTemplate.save(question);
        // 修改可能改变发布状态，重算学生可见索引
        visibilityIndex.rebuild();
        return TeacherQuestionDetail.from(question);
    }

    /** 删除题目 */
    public void delete(String id) {
        TeacherQuestion question = mongoTemplate.findById(
                id,
                TeacherQuestion.class);
        if (question == null) {
            throw new NotFoundException("题目不存在");
        }
        mongoTemplate.remove(question);
        // 删除可能移除一道已发布题目，重算学生可见索引
        visibilityIndex.rebuild();
    }

    /** 发布 / 下架题目（发布后学生端才可见） */
    public TeacherQuestionDetail publish(String id, boolean published) {
        TeacherQuestion question = mongoTemplate.findById(
                id,
                TeacherQuestion.class);
        if (question == null) {
            throw new NotFoundException("题目不存在");
        }
        question.setPublished(published);
        mongoTemplate.save(question);
        // 发布/下架后重算学生可见索引（Redis）
        visibilityIndex.rebuild();
        return TeacherQuestionDetail.from(question);
    }

    /** 校验并填写题目字段（新增/修改共用） */
    private void apply(
            TeacherQuestion question,
            QuestionRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("题目标题不能为空");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new BadRequestException("题目描述不能为空");
        }
        if (request.methodName() == null || request.methodName().isBlank()) {
            throw new BadRequestException("方法名不能为空");
        }

        String categoryId = blankToNull(request.categoryId());
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new BadRequestException("所选分类不存在");
        }

        question.setTitle(request.title().trim());
        question.setDescription(request.description());
        question.setMethodName(request.methodName().trim());
        question.setMethodSignature(request.methodSignature());
        question.setLanguage(request.language());
        question.setDifficulty(request.difficulty());
        question.setCategoryId(categoryId);
        question.setTags(request.tags() == null
                ? new ArrayList<String>()
                : request.tags());
        question.setTestCases(request.testCases() == null
                ? new ArrayList<com.codejudge.platform.entity.QuestionTestCase>()
                : request.testCases());
        question.setPublished(Boolean.TRUE.equals(request.published()));
    }

    /** 空白字符串转 null，方便存库时统一表示「未分类」 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
