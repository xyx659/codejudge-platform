package com.codejudge.platform.repository;

import com.codejudge.platform.entity.CheatEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 防作弊事件数据访问接口（MongoDB，对应 cheat_events 集合）。
 */
public interface CheatEventRepository extends MongoRepository<CheatEvent, String> {

    /** 按考试 ID 查询该场考试的全部防作弊事件 */
    List<CheatEvent> findByExamId(String examId);
}
