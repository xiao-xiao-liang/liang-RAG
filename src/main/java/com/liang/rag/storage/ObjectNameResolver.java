package com.liang.rag.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对象存储 URL 解析工具
 * <p>
 * 从对象存储的完整 URL 中提取 objectName（去掉 endpoint/bucketName 前缀），
 * 用于后续的文件下载等操作。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectNameResolver {

    private final FileStorageStrategy fileStorageStrategy;

    /**
     * 从对象存储 URL 中提取对象名
     * <p>URL 格式: http://endpoint/bucketName/objectName</p>
     *
     * @param url 对象存储完整 URL
     * @return 对象名（如 converted/doc_1/xxx.md），如果解析失败返回 null
     */
    public String resolve(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        // 优先匹配当前存储策略的 URL 前缀
        String baseUrl = fileStorageStrategy.getFileUrl("");
        if (url.startsWith(baseUrl)) {
            return url.substring(baseUrl.length());
        }
        // 兜底：取最后一个 / 后的部分
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : null;
    }
}
