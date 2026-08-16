package com.codejudge.platform.service;

import com.codejudge.platform.common.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内接口限流服务。
 *
 * <p>采用 60 秒固定窗口，每个接口维度分别维护全局、单用户和单 IP 计数。</p>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final long WINDOW_MILLIS = 60_000;

    private final SystemConfigService systemConfigService;
    private final Map<String, Queue<Long>> buckets = new ConcurrentHashMap<>();
    private final AtomicLong operationCount = new AtomicLong();

    public RateLimitService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /** 登录接口限流 */
    public void checkLogin(String username, String clientIp) {
        RateLimitRuntimeConfig config =
                systemConfigService.getRateLimitRuntimeConfig();
        acquire("login", "登录",
                config.loginGlobal(),
                config.loginPerUser(),
                config.loginPerIp(),
                username,
                clientIp);
    }

    /** AI 调用限流，供后续 AI 评审服务调用 */
    public void checkAiCall(String username, String clientIp) {
        RateLimitRuntimeConfig config =
                systemConfigService.getRateLimitRuntimeConfig();
        acquire("ai", "AI调用",
                config.aiGlobal(),
                config.aiPerUser(),
                config.aiPerIp(),
                username,
                clientIp);
    }

    /** 代码提交限流 */
    public void checkSubmission(String username, String clientIp) {
        RateLimitRuntimeConfig config =
                systemConfigService.getRateLimitRuntimeConfig();
        acquire("submit", "代码提交",
                config.submitGlobal(),
                config.submitPerUser(),
                config.submitPerIp(),
                username,
                clientIp);
    }

    private void acquire(String dimension,
                         String displayName,
                         int globalLimit,
                         int userLimit,
                         int ipLimit,
                         String username,
                         String clientIp) {
        boolean globalAllowed = tryAcquire(
                dimension + ":global", globalLimit);
        boolean userAllowed = tryAcquire(
                dimension + ":user:" + normalize(username), userLimit);
        boolean ipAllowed = tryAcquire(
                dimension + ":ip:" + normalize(clientIp), ipLimit);

        maybeCleanup();
        if (!globalAllowed || !userAllowed || !ipAllowed) {
            log.warn("接口限流：type={}, username={}, clientIp={}",
                    displayName, normalize(username), normalize(clientIp));
            throw new RateLimitExceededException("请求过于频繁，请稍后重试");
        }
    }

    private boolean tryAcquire(String key, int limit) {
        Queue<Long> queue = buckets.computeIfAbsent(
                key, ignored -> new ConcurrentLinkedQueue<>());
        synchronized (queue) {
            long now = System.currentTimeMillis();
            while (!queue.isEmpty()
                    && now - queue.peek() > WINDOW_MILLIS) {
                queue.poll();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.add(now);
            return true;
        }
    }

    private void maybeCleanup() {
        long count = operationCount.incrementAndGet();
        if (count % 1000 != 0) {
            return;
        }
        buckets.entrySet().removeIf(entry -> {
            Queue<Long> queue = entry.getValue();
            synchronized (queue) {
                long now = System.currentTimeMillis();
                while (!queue.isEmpty()
                        && now - queue.peek() > WINDOW_MILLIS) {
                    queue.poll();
                }
                return queue.isEmpty();
            }
        });
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "anonymous";
        }
        return normalized.length() > 100
                ? normalized.substring(0, 100)
                : normalized;
    }
}
