package com.codejudge.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 评测服务（门面）。
 *
 * <p>职责：读取评测运行时配置、记录触发日志，并把实际评测委托给 {@link JudgeEngine}。
 * 当前引擎是占位实现 {@link StubJudgeEngine}（只打日志、不改状态）；
 * 接入真实评测引擎后只需替换引擎实现，本类与提交流程无需改动。</p>
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SystemConfigService systemConfigService;
    private final JudgeEngine judgeEngine;

    public JudgeService(SystemConfigService systemConfigService, JudgeEngine judgeEngine) {
        this.systemConfigService = systemConfigService;
        this.judgeEngine = judgeEngine;
    }

    /**
     * 触发评测。
     *
     * <p>TODO：接入评测引擎后，这里应改为「异步」执行（加 @Async），
     * 因为跑代码可能很慢，不能让提交接口卡在这里等评测结束。</p>
     *
     * @param submissionId 提交记录 ID（MySQL 的 submissions.id）
     */
    public void trigger(Long submissionId) {
        JudgeRuntimeConfig config = systemConfigService.getJudgeRuntimeConfig();
        log.info(
                "触发评测：submissionId={}, timeoutMs={}, memoryMb={}, maxConcurrent={}",
                submissionId,
                config.timeoutMs(),
                config.memoryMb(),
                config.maxConcurrent());
        judgeEngine.judge(submissionId);
    }
}
