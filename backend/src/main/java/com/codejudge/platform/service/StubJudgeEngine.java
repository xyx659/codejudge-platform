package com.codejudge.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 评测引擎占位实现。
 *
 * <p>真实评测引擎（如 Judge0 沙箱）尚未接入，这里只打印一条日志、不做任何写回，
 * 提交的 {@code judgeStatus} 会一直停留在 {@code PENDING}。接入真实引擎后删除本类即可。</p>
 */
@Component
public class StubJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(StubJudgeEngine.class);

    @Override
    public void judge(Long submissionId) {
        log.info("评测引擎未接入（占位），submissionId={} 保持 PENDING", submissionId);
    }
}
