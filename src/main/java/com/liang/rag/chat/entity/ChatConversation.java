package com.liang.rag.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.liang.rag.common.entity.BaseEntity;
import com.liang.rag.common.enums.ConversationStatus;
import lombok.*;

/**
 * AI会话实体
 * <p>对应数据库表 chat_conversation，记录用户与AI的会话信息</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
@EqualsAndHashCode(callSuper = true)
public class ChatConversation extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话唯一标识
     */
    private String conversationId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 状态（默认 active）
     */
    private ConversationStatus status;
}
