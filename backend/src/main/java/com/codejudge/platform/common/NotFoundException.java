package com.codejudge.platform.common;

/**
 * 资源不存在异常。
 *
 * <p>当查询的资源（题目、提交记录、成绩等）不存在、或当前用户无权访问时抛出，
 * 由 {@link GlobalExceptionHandler} 统一转换成 HTTP 404 返回给前端。</p>
 *
 * <p>好处：业务代码里只需要「throw 一个异常」，不用在每个接口里手写返回 404 的逻辑，
 * 错误处理集中在一个地方，风格统一。</p>
 */
public class NotFoundException extends RuntimeException {

    /** @param message 返回给前端的提示信息，如「题目不存在」 */
    public NotFoundException(String message) {
        super(message);
    }
}
