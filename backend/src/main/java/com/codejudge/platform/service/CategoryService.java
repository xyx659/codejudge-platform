package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.dto.CategoryRequest;
import com.codejudge.platform.entity.Category;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.TeacherQuestion;
import com.codejudge.platform.repository.CategoryRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 题目分类业务逻辑。
 *
 * <p>约定：Controller 只负责收请求，真正的业务规则都在 Service 里写。</p>
 */
@Service
public class CategoryService {

    /** 分类仓库：负责 categories 集合的增删改查 */
    private final CategoryRepository categoryRepository;

    /** MongoTemplate：用来统计「某分类下是否还有题目/考试」，做删除前的引用检查 */
    private final MongoTemplate mongoTemplate;

    public CategoryService(CategoryRepository categoryRepository, MongoTemplate mongoTemplate) {
        this.categoryRepository = categoryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** 查询全部分类，按排序号升序返回 */
    public List<Category> list() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }

    /** 新增分类 */
    public Category create(CategoryRequest request) {
        // 分类名称必填
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("分类名称不能为空");
        }
        Category category = new Category(
                request.name().trim(),
                request.description(),
                request.sortOrder() == null ? 0 : request.sortOrder());
        return categoryRepository.save(category);
    }

    /** 修改分类 */
    public Category update(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("分类不存在"));
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("分类名称不能为空");
        }
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        return categoryRepository.save(category);
    }

    /** 删除分类：若分类下仍有题目或考试，则拒绝删除，避免出现「孤儿数据」 */
    public void delete(String id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("分类不存在"));

        long questionCount = mongoTemplate.count(
                new Query(Criteria.where("categoryId").is(id)), TeacherQuestion.class);
        long examCount = mongoTemplate.count(
                new Query(Criteria.where("categoryId").is(id)), Exam.class);
        if (questionCount > 0 || examCount > 0) {
            throw new BadRequestException("该分类下仍有题目或考试，请先移走后再删除");
        }
        categoryRepository.deleteById(id);
    }
}
