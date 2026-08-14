package com.codejudge.platform.common;

/**
 * 统一 API 响应体。
 *
 * <p>约定：{@code code} 为 0 表示成功，非 0 表示失败；
 * {@code message} 为提示信息；{@code data} 为业务数据（可为 null）。</p>
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> {

    /** 状态码：0 成功，非 0 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 构造成功响应 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<T>(0, "ok", data);
    }

    /** 构造失败响应 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<T>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
