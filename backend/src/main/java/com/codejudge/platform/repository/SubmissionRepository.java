package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提交元数据访问接口（JPA，对应 MySQL submissions 表）。
 */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
}
