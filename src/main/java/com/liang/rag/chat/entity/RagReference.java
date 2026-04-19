package com.liang.rag.chat.entity;

import com.liang.rag.common.enums.RetrievalSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * RAG引用内容内部类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagReference {
    /**
     * 文档ID
     */
    private String documentId;

    /**
     * 文档URL
     */
    private String url;

    /**
     * 文档标题
     */
    private String documentTitle;

    /**
     * 文档块ID
     */
    private String chunkId;

    /**
     * 文档块内容
     */
    private String chunkContent;

    /**
     * 相似度分数
     */
    private Double similarityScore;

    private Double rerankScore;

    /**
     * 检索来源：vector/keyword/hybrid/rerank
     */
    private RetrievalSource retrievalSource;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
}