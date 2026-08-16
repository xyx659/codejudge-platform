package com.codejudge.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 评测服务（占位）。
 *
 * <p>职责：执行学生代码、比对测试用例、产出评测结果与 AI 评审。
 * 目前评测引擎（如 Judge0 沙箱）尚未接入，这里只打印一条触发日志；
 * 真正接入后，只需替换本类实现，提交流程无需改动。</p>
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    /**
     * 触发评测。
     *
     * <p>TODO：接入评测引擎后，这里应改为「异步」执行（加 @Async），
     * 因为跑代码可能很慢，不能让提交接口卡在这里等评测结束。</p>
     *
     * @param submissionId 提交记录 ID（MySQL 的 submissions.id）
     */
    public void trigger(Long submissionId) {
        // 占位实现：将来在这里把 submissionId 交给评测引擎处理
        log.info("已触发评测：submissionId={}（评测引擎待接入，当前状态保持 PENDING）", submissionId);
    }
}
