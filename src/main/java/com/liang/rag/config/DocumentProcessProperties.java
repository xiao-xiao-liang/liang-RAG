package com.liang.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档处理配置属性
 * <p>
 * 管理文档异步转换过程中所需的本地临时目录等配置。
 * ZIP 解压、图片处理等中间文件将存放在此目录下，处理完成后自动清理。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "document.process")
public class DocumentProcessProperties {

    /** 本地临时文件工作目录 */
    private String tempDir = "./temp/doc-process";

    /** 用于图片描述生成的多模态视觉模型名称 */
    private String visionModel = "qwen3-vl-plus";
}
