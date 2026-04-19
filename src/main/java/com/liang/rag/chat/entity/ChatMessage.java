package com.liang.rag.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.liang.rag.common.entity.BaseEntity;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 会话消息实体
 * <p>对应数据库表 chat_message，记录会话中的每条消息详情</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "chat_message", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class ChatMessage extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息唯一标识
     */
    private String messageId;

    /**
     * 所属会话ID
     */
    private String conversationId;

    /**
     * 角色：USER/ASSISTANT
     */
    private String type;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 改写后的内容
     */
    private String rewriteContent;

    /**
     * Token数量
     */
    private Integer tokenCount;

    /**
     * 使用的模型名称
     */
    private String modelName;

    /**
     * RAG引用内容JSON数组
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RagReference> ragReferences;

    /**
     * 扩展元数据（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}
