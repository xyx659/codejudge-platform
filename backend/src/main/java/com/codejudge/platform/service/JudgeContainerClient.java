package com.codejudge.platform.service;

import com.codejudge.platform.dto.ContainerRunRequest;
import com.codejudge.platform.dto.ContainerRunResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 评测容器客户端：封装一次「无状态单命令」的 Docker 容器全生命周期。
 *
 * <p>流程：create → {@code docker cp} 写工作目录 → start → wait（带超时）→ 读日志 → 清理。
 * 超时后按「进优雅停止 → 强杀 → 强制删除」三级兜底，确保容器不残留。</p>
 *
 * <p>安全参数（详见设计文档 §五）：禁网、限内存/CPU/进程数、只读根 + tmpfs、去除全部 Linux capability、
 * 以 {@code nobody} 运行。镜像与工作目录可配置。</p>
 */
@Component
public class JudgeContainerClient {

    private static final Logger log = LoggerFactory.getLogger(JudgeContainerClient.class);

    /** 镜像名，默认 openjdk:17 */
    private final String image;

    private final DockerClient dockerClient;

    /** 镜像是否已确认存在，避免每次运行都触发 inspect/pull 竞态 */
    private volatile boolean imageReady = false;

    public JudgeContainerClient(DockerClient dockerClient,
                                @Value("${judge.docker.image:openjdk:17}") String image) {
        this.dockerClient = dockerClient;
        this.image = image;
    }

    /** 编译类操作结果：退出码 + stderr + 回拷的工作目录 tar（编译失败/超时为 null）+ 是否超时。 */
    public record CompileResult(int exitCode, String stderr, byte[] outputTar, boolean timedOut) {
    }

    /**
     * 执行一次容器运行。
     *
     * @param request 运行入参
     * @return 退出码 + 标准输出/错误 + 是否超时
     */
    public ContainerRunResult run(ContainerRunRequest request) {
        String containerId = null;
        try {
            ensureImage();
            containerId = createContainer(request);
            copyWorkDir(containerId, request.workDir(), request.workDirTar());
            dockerClient.startContainerCmd(containerId).exec();

            Integer exitCode = dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(request.timeoutMs(), TimeUnit.MILLISECONDS);

            String[] logs = readLogs(containerId);

            if (exitCode == null) {
                // 超时：三级强杀，避免容器残留
                forceKill(containerId);
                log.warn("评测容器执行超时，已强杀：containerId={}", containerId);
                return new ContainerRunResult(-1, logs[0], logs[1], true);
            }

            removeContainer(containerId);
            return new ContainerRunResult(exitCode, logs[0], logs[1], false);
        } catch (Exception e) {
            log.error("容器执行异常：containerId={}", containerId, e);
            // 兜底清理，防止泄漏
            if (containerId != null) {
                forceKill(containerId);
            }
            return new ContainerRunResult(-1, "", e.getMessage(), false);
        }
    }

    /**
     * 执行一次编译类操作：编译成功后额外把容器工作目录回拷为 tar（含 .class 产物），
     * 供逐用例运行容器复用。编译失败 / 超时 / 异常时 {@code outputTar} 为 {@code null}。
     */
    public CompileResult compile(ContainerRunRequest request) {
        String containerId = null;
        try {
            ensureImage();
            containerId = createContainer(request);
            copyWorkDir(containerId, request.workDir(), request.workDirTar());
            dockerClient.startContainerCmd(containerId).exec();

            Integer exitCode = dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(request.timeoutMs(), TimeUnit.MILLISECONDS);

            String[] logs = readLogs(containerId);

            if (exitCode == null) {
                forceKill(containerId);
                log.warn("编译容器执行超时，已强杀：containerId={}", containerId);
                return new CompileResult(-1, logs[1], null, true);
            }
            if (exitCode != 0) {
                removeContainer(containerId);
                return new CompileResult(exitCode, logs[1], null, false);
            }

            byte[] outputTar = fetchWorkDir(containerId, request.workDir());
            removeContainer(containerId);
            return new CompileResult(exitCode, logs[1], outputTar, false);
        } catch (Exception e) {
            log.error("编译容器执行异常：containerId={}", containerId, e);
            if (containerId != null) {
                forceKill(containerId);
            }
            return new CompileResult(-1, e.getMessage(), null, false);
        }
    }

    /** 从容器回拷工作目录（{@code docker cp} 反向），返回 tar 字节。 */
    private byte[] fetchWorkDir(String containerId, String workDir) {
        try (InputStream in = dockerClient.copyArchiveFromContainerCmd(containerId, workDir).exec()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("回拷编译产物失败：containerId=" + containerId, e);
        }
    }

    /** 首次运行时检查并自动拉取评测镜像（双重检查锁，避免并发重复拉取）。 */
    private void ensureImage() {
        if (imageReady) {
            return;
        }
        synchronized (this) {
            if (imageReady) {
                return;
            }
            try {
                dockerClient.inspectImageCmd(image).exec();
            } catch (NotFoundException e) {
                log.info("评测镜像不存在，开始拉取：image={}", image);
                try {
                    dockerClient.pullImageCmd(image)
                            .exec(new PullImageResultCallback())
                            .awaitCompletion();
                    log.info("评测镜像拉取完成：image={}", image);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("拉取评测镜像被中断：image=" + image, ie);
                }
            }
            imageReady = true;
        }
    }

    /** 创建容器并设置资源/安全限制。 */
    private String createContainer(ContainerRunRequest request) {
        long memoryBytes = (long) request.memoryMb() * 1024 * 1024;
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)     // 禁 swap，避免内存超限被换出
                .withNanoCPUs((long) (request.cpus() * 1_000_000_000L))
                .withPidsLimit(64L)              // 防 fork bomb
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/tmp", "rw"))
                .withCapDrop(Capability.ALL)
                .withNetworkMode("none");

        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withCmd(request.command())
                .withWorkingDir(request.workDir())
                .withUser("nobody")
                .withHostConfig(hostConfig)
                .exec();
        return response.getId();
    }

    /** 通过 {@code docker cp} 把源码/工作目录 tar 包写入容器工作目录。 */
    private void copyWorkDir(String containerId, String workDir, byte[] workDirTar) {
        if (workDirTar == null || workDirTar.length == 0) {
            return;
        }
        dockerClient.copyArchiveToContainerCmd(containerId)
                .withTarInputStream(new ByteArrayInputStream(workDirTar))
                .withRemotePath(workDir)
                .withNoOverwriteDirNonDir(false)
                .exec();
    }

    /** 读取容器标准输出/错误。 */
    private String[] readLogs(String containerId) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        ResultCallback<Frame> callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame item) {
                String text = new String(item.getPayload(), StandardCharsets.UTF_8);
                if (item.getStreamType() == StreamType.STDERR) {
                    stderr.append(text);
                } else {
                    stdout.append(text);
                }
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }
        };
        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(false)
                .exec(callback);
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new String[]{stdout.toString(), stderr.toString()};
    }

    /** 超时兜底：优雅停止 → 强杀 → 强制删除。 */
    private void forceKill(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(1).exec();
        } catch (Exception ignored) {
        }
        try {
            dockerClient.killContainerCmd(containerId).exec();
        } catch (Exception ignored) {
        }
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception ignored) {
        }
    }

    /** 正常路径：按非强制方式删除容器。 */
    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(false).exec();
        } catch (Exception e) {
            log.debug("删除容器失败（忽略）：containerId={}", containerId, e);
        }
    }
}