package com.liang.rag.document.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.liang.rag.common.entity.BaseEntity;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.common.enums.KnowledgeBaseType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 知识文档实体
 *
 * @author liang
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_document")
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocument extends BaseEntity {

    /** 文档ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long docId;

    /** 文档标题 */
    private String docTitle;

    /** 上传用户 */
    private String uploadUser;

    /** 原始文档URL（存储在对象存储中） */
    private String docUrl;

    /** 转换后的文档URL（Markdown/ZIP） */
    private String convertedDocUrl;

    /** 文档处理状态 */
    private DocumentStatus status;

    /** 可见范围 */
    private String accessibleBy;

    /** 扩展字段（JSON） */
    private String extension;

    /** 补偿重试次数（默认 0，上限 3） */
    private Integer retryCount;

    /**
     * 文档描述
     */
    private String description;

    /**
     * 知识库类型
     */
    private KnowledgeBaseType knowledgeBaseType;

    /**
     * 表名（针对DATA_QUERY）
     */
    private String tableName;

    /**
     * 是否覆盖（针对DATA_QUERY）
     */
    @TableField(exist = false)
    private boolean override;
}
