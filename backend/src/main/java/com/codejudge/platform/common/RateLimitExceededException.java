package com.codejudge.platform.common;

/**
 * 接口请求超过限流阈值异常。
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
