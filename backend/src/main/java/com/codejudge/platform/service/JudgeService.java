package com.codejudge.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

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

    /** 动态并发限流器：maxConcurrent 变更时重建 Semaphore。 */
    private final Object limitLock = new Object();
    private Semaphore semaphore;
    private int semaphorePermits = -1;

    public JudgeService(SystemConfigService systemConfigService, JudgeEngine judgeEngine) {
        this.systemConfigService = systemConfigService;
        this.judgeEngine = judgeEngine;
    }

    /**
     * 触发评测（异步）。
     *
     * <p>{@code @Async("judgeExecutor")} 提交到评测专用线程池，评测耗时不会阻塞提交流程。
     * 进入执行体后先通过 Semaphore 抢占许可（阻塞等待空位），拿到许可再委托 {@link JudgeEngine}，
     * 从而把「同时评测」的数量控制在 {@code maxConcurrent} 以内。</p>
     *
     * @param submissionId 提交记录 ID（MySQL 的 submissions.id）
     */
    @Async("judgeExecutor")
    public void trigger(Long submissionId) {
        JudgeRuntimeConfig config = systemConfigService.getJudgeRuntimeConfig();
        int maxConcurrent = Math.max(1, config.maxConcurrent());
        Semaphore current = ensureSemaphore(maxConcurrent);
        try {
            current.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待评测许可被中断，跳过本次评测：submissionId={}", submissionId, e);
            return;
        }
        try {
            log.info(
                    "触发评测：submissionId={}, timeoutMs={}, memoryMb={}, maxConcurrent={}",
                    submissionId,
                    config.timeoutMs(),
                    config.memoryMb(),
                    config.maxConcurrent());
            judgeEngine.judge(submissionId);
        } catch (Exception e) {
            // 异步线程内的异常不会回传给提交方，这里统一记日志，避免丢失调用栈。
            log.error("评测执行失败：submissionId={}", submissionId, e);
        } finally {
            current.release();
        }
    }

    /**
     * 获取与 {@code permits} 匹配的 Semaphore；许可数变化时重建。
     *
     * <p>运行期修改 maxConcurrent 会立即切换新限流器；重建瞬间，正在旧限流器上排队的任务
     * 计数不会迁移到新限流器，可能产生极短暂的并发偏差，可接受。</p>
     */
    private Semaphore ensureSemaphore(int permits) {
        synchronized (limitLock) {
            if (semaphore == null || semaphorePermits != permits) {
                semaphore = new Semaphore(permits);
                semaphorePermits = permits;
            }
            return semaphore;
        }
    }
}
