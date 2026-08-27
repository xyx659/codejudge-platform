package com.codejudge.platform.dto;

import java.util.List;

/**
 * 一次「无状态单命令」容器执行的入参。
 *
 * @param command    容器内要执行的命令，如 {@code ["sh", "-c", "javac Solution.java"]}
 * @param workDir    容器内工作目录（需落在 tmpfs 挂载点内才可写，如 {@code /tmp/work}）
 * @param workDirTar 通过 {@code docker cp} 拷入工作目录的 tar 包字节，可为 {@code null}
 * @param timeoutMs  单次执行超时（毫秒）
 * @param memoryMb   内存上限（MB）
 * @param cpus       CPU 上限（如 1.0）
 */
public record ContainerRunRequest(
        List<String> command,
        String workDir,
        byte[] workDirTar,
        long timeoutMs,
        int memoryMb,
        double cpus) {
}