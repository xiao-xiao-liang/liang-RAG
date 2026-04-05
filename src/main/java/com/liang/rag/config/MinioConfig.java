package com.liang.rag.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(properties.getEndpoint())
                    .credentials(properties.getAccessKey(), properties.getSecretKey())
                    .build();
            log.info("MinIO 客户端初始化成功，endpoint: {}", properties.getEndpoint());
            return client;
        } catch (Exception e) {
            log.error("MinIO 客户端初始化失败: {}", e.getMessage(), e);
            throw new IllegalStateException("MinIO 客户端初始化失败", e);
        }
    }
}
