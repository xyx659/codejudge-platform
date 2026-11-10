package com.codejudge.platform.repository;

import com.codejudge.platform.entity.DiagnosisReportDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 教学诊断报告数据访问接口（MongoDB，对应 diagnosis_reports 集合）。
 *
 * <p>主键为考试 ID，一场考试对应一条报告，故无需额外声明自定义查询方法。</p>
 */
public interface DiagnosisReportRepository extends MongoRepository<DiagnosisReportDoc, String> {
}
