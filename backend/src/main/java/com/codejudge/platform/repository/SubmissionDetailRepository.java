package com.codejudge.platform.repository;

import com.codejudge.platform.entity.SubmissionDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * 提交明细访问接口（MongoDB，对应 submission_details 集合）。
 */
public interface SubmissionDetailRepository extends MongoRepository<SubmissionDetail, String> {

    /**
     * 按「提交 ID + 学生 ID」查提交明细。
     *
     * <p>两个条件一起用，既定位到某次提交，又确保这条明细属于该学生（安全）。</p>
     */
    Optional<SubmissionDetail> findBySubmissionIdAndStudentId(Long submissionId, Long studentId);
}
