package com.liang.rag.parser;

import com.liang.rag.common.enums.FileType;
import com.liang.rag.common.enums.KnowledgeBaseType;
import com.liang.rag.document.entity.KnowledgeDocument;

import java.io.InputStream;

/**
 * 文件处理服务
 */
public interface FileProcessService {

    /**
     * 处理文档转换
     *
     * @param document       文档对象
     * @param inputStream    文件输入流
     * @param splitParamJson 切分参数 JSON（透传给下游切分消费者，可为空）
     */
    void processDocument(KnowledgeDocument document, InputStream inputStream, String splitParamJson);

    /**
     * 判断是否支持该文件类型
     *
     * @param fileType          文件类型
     * @param knowledgeBaseType 知识库类型
     * @return 是否支持
     */
    boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType);
}
