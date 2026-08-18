package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Exam;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 考试数据访问接口（MongoDB，对应 exams 集合）。
 */
public interface ExamRepository extends MongoRepository<Exam, String> {

    /**
     * 按状态查询考试（如查所有已发布 PUBLISHED 的考试）。
     *
     * <p>方法名「findByStatus」是 Spring Data 约定，自动生成 where status = ? 查询。</p>
     */
    List<Exam> findByStatus(String status);
}
