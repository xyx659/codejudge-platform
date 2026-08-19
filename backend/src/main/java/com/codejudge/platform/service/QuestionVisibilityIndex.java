package com.codejudge.platform.service;

import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.ExamQuestion;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.repository.ExamRepository;
import com.codejudge.platform.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 学生端题目可见性索引（Redis）。
 *
 * <p>学生只能看到「教师端发布的题目」——既包括单题发布（published=true），
 * 也包括已发布试卷（考试）里的题目。本组件用 Redis 的 Set 维护
 * 这些题目的 ID 集合，作为「哪些题对学生可见」的权威索引：</p>
 * <ul>
 *   <li>教师发布试卷 → {@link #rebuild()} 把该试卷里的题目 ID 写入 Redis Set；</li>
 *   <li>关闭 / 删除 / 修改试卷 → 同样触发 {@link #rebuild()} 重算；</li>
 *   <li>学生端列表 / 详情 / 提交都通过 {@link #getVisibleIds()} / {@link #isVisible(String)}
 *       判断可见性。</li>
 * </ul>
 *
 * <p><b>容错：</b>Redis 不可用或索引为空时，回退到 MongoDB 直接按 {@code exams.status=PUBLISHED}
 * 计算并尽量回写，保证没有 Redis 时功能仍可用。</p>
 */
@Component
public class QuestionVisibilityIndex {

    private static final Logger log = LoggerFactory.getLogger(QuestionVisibilityIndex.class);

    /** Redis Set 键：当前对学生可见的题目 ID 集合 */
    private static final String KEY = "codejudge:visible:question:ids";

    private final StringRedisTemplate redisTemplate;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;

    public QuestionVisibilityIndex(StringRedisTemplate redisTemplate,
                                   ExamRepository examRepository,
                                   QuestionRepository questionRepository) {
        this.redisTemplate = redisTemplate;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * 重新计算可见题目 ID（所有已发布试卷里题目的并集）并写回 Redis。
     *
     * <p>在试卷「发布 / 关闭 / 删除 / 修改」后调用，保持索引与 MongoDB 一致。</p>
     */
    public void rebuild() {
        Set<String> ids = computeVisibleIds();
        try {
            redisTemplate.delete(KEY);
            if (!ids.isEmpty()) {
                redisTemplate.opsForSet().add(KEY, ids.toArray(new String[0]));
            }
        } catch (Exception e) {
            // Redis 不可用只记日志，不影响本次发布/关闭等主流程
            log.warn("可见题目索引写回 Redis 失败（Redis 不可用？）：{}", e.getMessage());
        }
    }

    /**
     * 查询当前可见的题目 ID 集合。
     *
     * <p>优先读 Redis；索引为空或 Redis 不可用时，回退到 MongoDB 计算并尽量回写。</p>
     */
    public Set<String> getVisibleIds() {
        try {
            Set<String> ids = redisTemplate.opsForSet().members(KEY);
            if (ids != null && !ids.isEmpty()) {
                return ids;
            }
        } catch (Exception e) {
            log.warn("读取可见题目索引失败，回退 MongoDB：{}", e.getMessage());
        }
        Set<String> ids = computeVisibleIds();
        // 尽量把刚算出来的结果回写，供后续请求命中
        try {
            redisTemplate.delete(KEY);
            if (!ids.isEmpty()) {
                redisTemplate.opsForSet().add(KEY, ids.toArray(new String[0]));
            }
        } catch (Exception ignored) {
            // 回写失败不影响本次返回
        }
        return ids;
    }

    /** 判断某道题当前是否对学生可见（是否出现在已发布试卷中） */
    public boolean isVisible(String questionId) {
        return getVisibleIds().contains(questionId);
    }

    /** 从 MongoDB 计算可见题目 ID：单题发布（published=true）与已发布试卷（PUBLISHED）里题目的并集 */
    private Set<String> computeVisibleIds() {
        Set<String> ids = new HashSet<>();

        // 1. 教师端「发布/下架题目」：published=true 的题目直接对学生可见
        for (Question q : questionRepository.findByPublishedTrue()) {
            if (q.getId() != null) {
                ids.add(q.getId());
            }
        }

        // 2. 已发布试卷（考试）里的题目：发布试卷即让卷内题目对学生可见
        List<Exam> published = examRepository.findByStatus("PUBLISHED");
        for (Exam exam : published) {
            if (exam.getQuestions() == null) {
                continue;
            }
            for (ExamQuestion q : exam.getQuestions()) {
                if (q.getQuestionId() != null) {
                    ids.add(q.getQuestionId());
                }
            }
        }
        return ids;
    }
}
