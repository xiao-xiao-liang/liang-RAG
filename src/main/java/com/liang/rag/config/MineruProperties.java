package com.liang.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinerU 文档解析服务配置属性
 *
 * @author liang
 */
@Data
@Component
@ConfigurationProperties(prefix = "mineru")
public class MineruProperties {

    /** MinerU 服务地址 */
    private String url = "http://64.176.13.41:8000";

    /** 连接超时时间（毫秒） */
    private int connectTimeout = 30000;

    /** 响应超时时间（毫秒），PDF 大文件解析可能需要较长时间 */
    private int responseTimeout = 300000;
}
