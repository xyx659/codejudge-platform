package com.codejudge.platform.service;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 临时工作目录打包工具：把评测相关的源码 / .class / 输入文件打成 tar 字节，
 * 供 {@link JudgeContainerClient} 通过 {@code docker cp} 写入容器，并在编译后解包回拷的产物。
 *
 * <p>依赖 commons-compress（由 docker-java-core 传递引入）。目录扁平，仅保留文件名最后一段。</p>
 */
@Component
public class WorkspacePacker {

    /** 把「文件名 → 内容」集合打成 tar 字节。 */
    public byte[] pack(Map<String, byte[]> files) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bos)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                byte[] content = e.getValue();
                TarArchiveEntry entry = new TarArchiveEntry(e.getKey());
                entry.setSize(content.length);
                entry.setMode(0644);
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