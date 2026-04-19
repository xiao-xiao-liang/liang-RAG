package com.liang.rag.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.rag.chat.entity.ChatMessage;
import com.liang.rag.chat.entity.RagReference;
import com.liang.rag.chat.mapper.ChatMessageMapper;
import com.liang.rag.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话消息服务实现类
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    private static final String TYPE_USER = "USER";
    private static final String TYPE_ASSISTANT = "ASSISTANT";

    @Override
    public List<ChatMessage> getByConversationId(String conversationId) {
        return lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreateTime)
                .list();
    }

    @Override
    public ChatMessage getMessage(String messageId) {
        return lambdaQuery()
                .eq(ChatMessage::getMessageId, messageId)
                .oneOpt()
                .orElseThrow(() -> new IllegalArgumentException("消息不存在: " + messageId));
    }

    @Override
    public ChatMessage saveUserMessage(String conversationId, String content) {
        ChatMessage message = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .conversationId(conversationId)
                .type(TYPE_USER)
                .content(content)
                .build();
        save(message);
        log.info("保存用户消息成功, messageId={}, conversationId={}", message.getMessageId(), conversationId);
        return message;
    }

    @Override
    public void updateRewriteContent(String messageId, String rewriteContent) {
        LambdaUpdateWrapper<ChatMessage> updateWrapper = new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId)
                .set(ChatMessage::getRewriteContent, rewriteContent);
        boolean updated = update(updateWrapper);
        if (!updated) {
            throw new IllegalArgumentException("消息不存在或更新失败: " + messageId);
        }
    }

    @Override
    public ChatMessage saveAssistantMessage(String conversationId, String content, String modelName,
                                            Integer tokenCount, List<RagReference> ragReferences,
                                            Map<String, Object> metadata) {
        ChatMessage message = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .conversationId(conversationId)
                .type(TYPE_ASSISTANT)
                .content(content)
                .modelName(modelName)
                .tokenCount(tokenCount)
                .ragReferences(ragReferences)
                .metadata(metadata)
                .build();
        save(message);
        log.info("保存AI消息成功, messageId={}, conversationId={}, model={}",
                message.getMessageId(), conversationId, modelName);
        return message;
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        lambdaUpdate()
                .eq(ChatMessage::getConversationId, conversationId)
                .remove();
        log.info("删除会话消息成功（逻辑删除）, conversationId={}", conversationId);
    }

    @Override
    public List<ChatMessage> getRecentMessages(String conversationId, int limit) {
        // 先按时间倒序取最近N条
        List<ChatMessage> messages = lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + limit)
                .list();
        // 反转为时间正序，方便构建上下文窗口
        Collections.reverse(messages);
        return messages;
    }
}
