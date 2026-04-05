package com.liang.rag.document.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.liang.rag.common.enums.ChunkStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块实体
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {

    /** 片段ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文本内容 */
    private String text;

    /** 分片ID（UUID，全局唯一） */
    private String chunkId;

    /** 元数据（JSON 格式） */
    private String metadata;

    /** 所属文档ID */
    private Long documentId;

    /** 片段顺序（在文档中的位置） */
    private Integer chunkOrder;

    /** 向量存储中的嵌入ID */
    private String embeddingId;

    /** 状态 */
    private ChunkStatus status;

    /** 是否跳过嵌入生成（1=跳过） */
    private Integer skipEmbedding;
}
