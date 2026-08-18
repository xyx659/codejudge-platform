package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 教师端扩展的「提交记录」查询接口（JPA，对应 MySQL submissions 表）。
 *
 * <p>为什么单独建这个仓库、而不在团队的 {@link SubmissionRepository} 里加方法：</p>
 * <ul>
 *   <li>教师端监考/学情分析需要「按题目 ID 批量查提交记录」，团队已有的仓库没有这个方法。</li>
 *   <li>Spring Data 允许同一个实体对应多个仓库接口（各自生成独立 Bean），
 *       新建本接口即可，<b>无需改动团队写定的 {@link SubmissionRepository}</b>。</li>
 * </ul>
 */
public interface SubmissionQueryRepository extends JpaRepository<Submission, Long> {

    /**
     * 按题目 ID 集合批量查询提交记录。
     *
     * <p>监考与学情分析时，先把某场考试的题目 ID 全部取出来，
     * 再一次性查出这些题目的所有提交，从而计算每个学生的得分。</p>
     *
     * @param questionIds 题目 ID 集合（对应 MongoDB questions._id）
     * @return 这些题目的所有提交记录
     */
    List<Submission> findByQuestionIdIn(List<String> questionIds);
}
