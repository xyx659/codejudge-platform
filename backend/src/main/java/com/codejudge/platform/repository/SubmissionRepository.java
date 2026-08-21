package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 提交元数据访问接口（JPA，对应 MySQL submissions 表）。
 */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * 按学生 ID 分页查询提交记录。
     *
     * <p>方法名「findByStudentId」是 Spring Data 的约定：它会根据方法名
     * 自动生成查询（where student_id = ?），再配合 Pageable 实现分页。</p>
     */
    Page<Submission> findByStudentId(Long studentId, Pageable pageable);

    /** 判断学生是否已经产生提交记录 */
    boolean existsByStudentId(Long studentId);

    /** 判断题目是否已经产生提交记录 */
    boolean existsByQuestionId(String questionId);

    /** 查某个学生对某道题的提交（每题限一次，用于「提交后回看」） */
    Optional<Submission> findFirstByStudentIdAndQuestionId(Long studentId, String questionId);

    /** 批量查某个学生对若干道题的提交（用于列表标记「已提交」） */
    List<Submission> findByStudentIdAndQuestionIdIn(Long studentId, Collection<String> questionIds);

    /** 查某个学生对某场考试的任意一条提交（整卷一次交卷，用于判断「是否已交卷」） */
    Optional<Submission> findFirstByStudentIdAndExamId(Long studentId, String examId);

    /** 查某个学生在某场考试里的全部提交（按题展开，用于交卷后回看每题答案） */
    List<Submission> findByStudentIdAndExamId(Long studentId, String examId);
}
