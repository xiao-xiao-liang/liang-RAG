package com.liang.rag.storage.impl;

import com.liang.rag.common.convention.exception.ServiceException;
import com.liang.rag.config.MinioProperties;
import com.liang.rag.storage.FileStorageStrategy;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储策略实现
 * <p>
 * 当 {@code storage.type=minio}（默认）时激活。
 * 封装 MinIO SDK 的文件上传、下载、删除、预签名 URL 等操作。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioFileStorageStrategy implements FileStorageStrategy {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String upload(MultipartFile file, String objectName) {
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return getFileUrl(objectName);
        } catch (Exception e) {
            log.error("MinIO 文件上传失败, objectName: {}", objectName, e);
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public String upload(String objectName, byte[] content, String contentType) {
        try {
            ensureBucketExists();
            try (InputStream stream = new ByteArrayInputStream(content)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectName)
                        .stream(stream, content.length, -1)
                        .contentType(contentType)
                        .build());
            }
            return getFileUrl(objectName);
        } catch (Exception e) {
            log.error("MinIO 字节数组上传失败, objectName: {}", objectName, e);
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件下载失败, objectName: {}", objectName, e);
            throw new ServiceException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件删除失败, objectName: {}", objectName, e);
            throw new ServiceException("文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败, objectName: {}", objectName, e);
            throw new ServiceException("生成预签名 URL 失败: " + e.getMessage());
        }
    }

    @Override
    public String getFileUrl(String objectName) {
        return String.format("%s/%s/%s", minioProperties.getEndpoint(), minioProperties.getBucketName(), objectName);
    }

    @Override
    public String getType() {
        return "minio";
    }

    /**
     * 确保存储桶存在，不存在则创建并设置公共读策略
     */
    private void ensureBucketExists() {
        try {
            String bucketName = minioProperties.getBucketName();
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                // 设置公共读策略
                String policy = """
                        {
                            "Version": "2012-10-17",
                            "Statement": [{
                                "Effect": "Allow",
                                "Principal": {"AWS": ["*"]},
                                "Action": ["s3:GetObject"],
                                "Resource": ["arn:aws:s3:::%s/*"]
                            }]
                        }
                        """.formatted(bucketName);
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(bucketName)
                                .config(policy)
                                .build());
                log.info("存储桶 {} 创建成功，已设置公共读策略", bucketName);
            }
        } catch (Exception e) {
            log.error("检查/创建存储桶失败", e);
            throw new ServiceException("存储桶初始化失败: " + e.getMessage());
        }
    }
}
