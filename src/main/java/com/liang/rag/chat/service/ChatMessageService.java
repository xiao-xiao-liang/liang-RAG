package com.liang.rag.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.rag.chat.entity.ChatMessage;
import com.liang.rag.chat.entity.RagReference;

import java.util.List;
import java.util.Map;

/**
 * 会话消息服务接口
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 根据会话ID查询消息列表
     * <p>按创建时间正序排列</p>
     *
     * @param conversationId 会话唯一标识
     * @return 消息列表
     */
    List<ChatMessage> getByConversationId(String conversationId);

    /**
     * 根据消息唯一标识获取消息
     *
     * @param messageId 消息唯一标识
     * @return 消息实体
     */
    ChatMessage getMessage(String messageId);

    /**
     * 保存用户消息
     *
     * @param conversationId 会话唯一标识
     * @param content        消息内容
     * @return 保存后的消息实体
     */
    ChatMessage saveUserMessage(String conversationId, String content);

    /**
     * 更新消息的改写内容
     *
     * @param messageId      消息唯一标识
     * @param rewriteContent 改写后的内容
     */
    void updateRewriteContent(String messageId, String rewriteContent);

    /**
     * 保存AI助手消息
     *
     * @param conversationId 会话唯一标识
     * @param content        消息内容
     * @param modelName      使用的模型名称
     * @param tokenCount     Token数量
     * @param ragReferences  RAG引用内容列表（可为空）
     * @param metadata       扩展元数据（可为空）
     * @return 保存后的消息实体
     */
    ChatMessage saveAssistantMessage(String conversationId, String content, String modelName,
                                     Integer tokenCount, List<RagReference> ragReferences,
                                     Map<String, Object> metadata);

    /**
     * 根据会话ID删除所有消息（逻辑删除）
     *
     * @param conversationId 会话唯一标识
     */
    void deleteByConversationId(String conversationId);

    /**
     * 获取会话的最近N条消息
     * <p>按创建时间倒序取N条后再正序返回，用于构建上下文窗口</p>
     *
     * @param conversationId 会话唯一标识
     * @param limit          消息数量上限
     * @return 消息列表（按时间正序）
     */
    List<ChatMessage> getRecentMessages(String conversationId, int limit);
}
