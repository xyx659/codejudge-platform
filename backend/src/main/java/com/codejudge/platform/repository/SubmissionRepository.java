package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
