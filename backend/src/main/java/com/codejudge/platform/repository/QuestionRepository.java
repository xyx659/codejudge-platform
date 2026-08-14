package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Question;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 题目数据访问接口（MongoDB，对应 questions 集合）。
 */
public interface QuestionRepository extends MongoRepository<Question, String> {
}
