package com.codejudge.platform.config;

import com.codejudge.platform.service.SystemConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置，主要用于审计日志写入。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /**
     * 评测专用线程池：为 {@code JudgeService.trigger} 的 {@code @Async} 提供执行线程。
     *
     * <p>core/max 取当前配置的 {@code maxConcurrent}，作为「最大执行容量」；
     * 真正的并发闸门由 {@link com.codejudge.platform.service.JudgeService} 内的动态 Semaphore 控制。
     * 运行期把 maxConcurrent 调大若超过此线程池容量，需重启才扩容（默认 10 已够用）。</p>
     */
    @Bean(name = "judgeExecutor")
    public Executor judgeExecutor(SystemConfigService systemConfigService) {
        int maxConcurrent = Math.max(1,
                systemConfigService.getJudgeRuntimeConfig().maxConcurrent());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(100000);
        executor.setThreadNamePrefix("judge-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
