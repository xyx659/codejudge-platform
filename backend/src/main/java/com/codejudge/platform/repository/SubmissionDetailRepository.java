package com.codejudge.platform.repository;

import com.codejudge.platform.entity.SubmissionDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 提交明细访问接口（MongoDB，对应 submission_details 集合）。
 */
public interface SubmissionDetailRepository extends MongoRepository<SubmissionDetail, String> {
}
