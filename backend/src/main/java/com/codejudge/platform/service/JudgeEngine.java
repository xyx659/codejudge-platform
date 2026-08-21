package com.codejudge.platform.service;

/**
 * 评测引擎抽象。
 *
 * <p>学生提交代码后，由 {@link JudgeService} 调用本接口执行评测：读取提交明细与题目测试用例，
 * 执行学生代码、逐用例比对输出，并把 {@code judgeStatus} / {@code score} / {@code testResults} /
 * {@code aiReview} 写回数据库。</p>
 *
 * <p>接入真实评测引擎（如 Judge0、本地沙箱）时，只需新增一个实现类并交给 Spring 管理，
 * 提交流程与对外接口无需改动。</p>
 */
public interface JudgeEngine {

    /**
     * 评测一次提交。
     *
     * @param submissionId 提交记录 ID（MySQL 的 submissions.id）
     */
    void judge(Long submissionId);
}
