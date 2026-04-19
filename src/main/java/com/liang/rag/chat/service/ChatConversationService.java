package com.liang.rag.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.rag.chat.entity.ChatConversation;

import java.util.List;

/**
 * AI会话服务接口
 *
 * @author liang
 */
public interface ChatConversationService extends IService<ChatConversation> {

    /**
     * 根据用户ID查询会话列表
     * <p>按创建时间倒序排列</p>
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatConversation> getConversationsByUserId(String userId);

    /**
     * 根据会话唯一标识获取会话
     *
     * @param conversationId 会话唯一标识
     * @return 会话实体
     */
    ChatConversation getConversation(String conversationId);

    /**
     * 创建新会话
     *
     * @param userId 用户ID
     * @param title  会话标题（可为空）
     * @return 创建后的会话 Id
     */
    String createConversation(String userId, String title);

    /**
     * 更新会话标题
     *
     * @param conversationId 会话唯一标识
     * @param title          新标题
     */
    void updateTitle(String conversationId, String title);

    /**
     * 归档会话
     * <p>将会话状态设置为 ARCHIVED</p>
     *
     * @param conversationId 会话唯一标识
     */
    void archiveConversation(String conversationId);

    /**
     * 删除会话（逻辑删除）
     *
     * @param conversationId 会话唯一标识
     */
    void deleteConversation(String conversationId);
}
