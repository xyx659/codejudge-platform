package com.codejudge.platform.dto;

/**
 * 容器执行结果。
 *
 * @param exitCode 容器退出码；超时被强杀时为 -1
 * @param stdout   标准输出
 * @param stderr   标准错误
 * @param timedOut 是否超时
 */
public record ContainerRunResult(int exitCode, String stdout, String stderr, boolean timedOut) {
}