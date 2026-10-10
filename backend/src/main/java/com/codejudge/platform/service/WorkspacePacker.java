package com.codejudge.platform.service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 临时工作目录打包工具：把评测相关的源码 / .class / 输入文件打成 tar 字节，
 * 供 {@link JudgeContainerClient} 通过 {@code docker cp} 写入容器，并在编译后解包回拷的产物。
 *
 * <p>依赖 commons-compress（由 docker-java-core 传递引入）。目录扁平，仅保留文件名最后一段。</p>
 */
@Component
public class WorkspacePacker {

    /** 把「文件名 → 内容」集合打成 tar 字节（全部 0644）。 */
    public byte[] pack(Map<String, byte[]> files) {
        return pack(files, Set.of());
    }

    /**
     * 把「文件名 → 内容」集合打成 tar 字节；{@code executables} 中的文件设 0755（含 other 执行位）。
     *
     * <p>编译产物（如 Go 的可执行文件 {@code solution}）需保留执行位，否则容器内 {@code nobody}
     * 无权限执行（{@code docker cp} 写入后文件属主为宿主机 uid，{@code nobody} 无法 {@code chmod}）。</p>
     */
    public byte[] pack(Map<String, byte[]> files, Set<String> executables) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bos)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                byte[] content = e.getValue();
                TarArchiveEntry entry = new TarArchiveEntry(e.getKey());
                entry.setSize(content.length);
                entry.setMode(executables.contains(e.getKey()) ? 0755 : 0644);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
            tar.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("打包临时工作目录失败", e);
        }
    }

    /** 解包 tar 字节为「文件名 → 内容」；只保留最后一段路径，兼容 {@code docker cp} 返回带目录前缀的情况。 */
    public Map<String, byte[]> unpack(byte[] tarBytes) {
        Map<String, byte[]> files = new TreeMap<>();
        try (TarArchiveInputStream tin =
                     new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))) {
            TarArchiveEntry entry;
            while ((entry = tin.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                if (slash >= 0) {
                    name = name.substring(slash + 1);
                }
                if (!name.isEmpty()) {
                    files.put(name, tin.readAllBytes());
                }
            }
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("解包临时工作目录失败", e);
        }
    }
}