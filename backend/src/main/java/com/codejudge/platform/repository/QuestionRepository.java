package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Question;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * 题目数据访问接口（MongoDB，对应 questions 集合）。
 */
public interface QuestionRepository extends MongoRepository<Question, String> {

    Optional<Question> findBySourcePlatformAndSourceId(
            String sourcePlatform,
            String sourceId);

    long countByPublishedTrue();

    /** 查所有已发布（published=true）的题目，用于学生端可见性索引 */
    List<Question> findByPublishedTrue();
}
