package com.codejudge.platform.dto;

/**
 * 登录响应体。
 *
 * @param token    JWT 令牌，后续请求放入 {@code Authorization: Bearer <token>} 头
 * @param username 登录账号
 * @param name     姓名
 * @param role     角色：ADMIN / TEACHER / STUDENT
 */
public record LoginResponse(String token, String username, String name, String role) {
}
