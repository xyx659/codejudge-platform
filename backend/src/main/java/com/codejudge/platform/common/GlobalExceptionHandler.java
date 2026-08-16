package com.codejudge.platform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 *
 * <p>拦截所有未捕获异常，统一转换为友好的 {@link ApiResponse}，
 * 避免向前端泄漏堆栈、SQL 等敏感信息。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 登录/认证失败：用户名或密码错误 */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
        return ApiResponse.error(401, "用户名或密码错误");
    }

    /** 参数校验失败（@Valid），返回第一条字段错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("参数错误");
        return ApiResponse.error(400, message);
    }

    /** 资源不存在：返回 404（如题目、提交记录查不到，或无权访问） */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFoundException(NotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    /** 业务参数或状态不合法：返回 400 */
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequestException(BadRequestException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    /** 配置等数据发生并发修改冲突：返回 409 */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException e) {
        return ApiResponse.error(409, "数据已被其他管理员修改，请刷新后重试");
    }

    /** 超过接口限流阈值：返回 429 */
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleRateLimitExceeded(RateLimitExceededException e) {
        return ApiResponse.error(429, e.getMessage());
    }

    /** 兜底处理：记录完整堆栈到日志，对外只返回通用错误信息 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.error(500, "服务器内部错误");
    }
}
