package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 分类数据访问接口（MongoDB，对应 categories 集合）。
 */
public interface CategoryRepository extends MongoRepository<Category, String> {

    /**
     * 按排序号升序返回全部分类。
     *
     * <p>方法名「findAllByOrderBySortOrderAsc」是 Spring Data 的约定：
     * 自动生成「按 sortOrder 升序」的查询，让分类列表按配置顺序展示。</p>
     */
    List<Category> findAllByOrderBySortOrderAsc();
}
