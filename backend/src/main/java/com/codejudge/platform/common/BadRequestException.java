package com.codejudge.platform.common;

/**
 * 业务请求不合法异常。
 *
 * <p>用于重复用户名、非法角色等业务校验失败场景，
 * 由 {@link GlobalExceptionHandler} 统一转换成 HTTP 400。</p>
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
