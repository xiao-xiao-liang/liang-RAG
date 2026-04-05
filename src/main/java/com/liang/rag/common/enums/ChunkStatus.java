package com.liang.rag.common.enums;

/**
 * 文档分块状态枚举
 */
public enum ChunkStatus {


    /**
     * 无需向量化，跳过，存入关系型数据库
     */
    SKIP_EMBEDDING,

    /**
     * 已存入向量数据库
     */
    VECTOR_STORED
}
