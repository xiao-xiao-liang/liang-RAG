package com.liang.rag.config.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 配置属性
 * <p>通过 {@code @ConfigurationProperties} 实现类型安全的配置绑定</p>
 *
 * @author liang
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.minio")
public class MinioProperties {

    /** MinIO 服务端点 */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 秘密密钥 */
    private String secretKey = "minioadmin";

    /** 默认存储桶名 */
    private String bucketName = "liang-rag";
}
