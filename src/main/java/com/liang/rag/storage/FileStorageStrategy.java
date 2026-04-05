package com.liang.rag.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储策略接口
 * <p>
 * 采用策略模式抽象文件存储操作，支持多种存储后端的切换与扩展。
 * 当前实现：MinIO；可扩展：阿里云 OSS、RustFS 等。
 * </p>
 * <p>通过 {@code storage.type} 配置项选择激活的存储策略。</p>
 *
 * @author liang
 * @see com.liang.rag.storage.impl.MinioFileStorageStrategy
 */
public interface FileStorageStrategy {

    /**
     * 上传 MultipartFile 文件
     *
     * @param file       上传的文件
     * @param objectName 存储对象名（含路径）
     * @return 文件访问 URL
     */
    String upload(MultipartFile file, String objectName);

    /**
     * 上传字节数组
     *
     * @param objectName  存储对象名（含路径）
     * @param content     文件内容字节数组
     * @param contentType MIME 类型
     * @return 文件访问 URL
     */
    String upload(String objectName, byte[] content, String contentType);

    /**
     * 下载文件
     *
     * @param objectName 存储对象名
     * @return 文件输入流（调用方负责关闭）
     */
    InputStream download(String objectName);

    /**
     * 删除文件
     *
     * @param objectName 存储对象名
     */
    void delete(String objectName);

    /**
     * 获取文件的预签名临时访问 URL
     *
     * @param objectName 存储对象名
     * @return 带签名的临时下载链接
     */
    String getPresignedUrl(String objectName);

    /**
     * 拼接并返回文件的公开访问 URL
     *
     * @param objectName 存储对象名
     * @return 文件 URL
     */
    String getFileUrl(String objectName);

    /**
     * 获取存储策略类型标识
     *
     * @return 类型名称（如 "minio"、"oss"）
     */
    String getType();
}
