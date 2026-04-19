package com.liang.rag.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.rag.chat.entity.ChatConversation;
import com.liang.rag.chat.mapper.ChatConversationMapper;
import com.liang.rag.chat.service.ChatConversationService;
import com.liang.rag.common.enums.ConversationStatus;
import com.liang.rag.infra.snowflake.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI会话服务实现类
 */
@Slf4j
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements ChatConversationService {

    @Override
    public List<ChatConversation> getConversationsByUserId(String userId) {
        return lambdaQuery()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getCreateTime)
                .list();
    }

    @Override
    public ChatConversation getConversation(String conversationId) {
        return lambdaQuery()
                .eq(ChatConversation::getConversationId, conversationId)
                .oneOpt()
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + conversationId));
    }

    @Override
    public String createConversation(String userId, String title) {
        String conversationId = UUID.randomUUID().toString().replace("-", "") + userId;
        ChatConversation conversation = ChatConversation.builder()
                .conversationId(conversationId)
                .userId(userId)
                .title(title)
                .status(ConversationStatus.ACTIVE)
                .build();
        save(conversation);
        log.info("创建会话成功, conversationId={}, userId={}", conversation.getConversationId(), userId);
        return conversationId;
    }

    @Override
    public void updateTitle(String conversationId, String title) {
        LambdaUpdateWrapper<ChatConversation> updateWrapper = new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .set(ChatConversation::getTitle, title);
        boolean updated = update(updateWrapper);
        if (!updated) {
            throw new IllegalArgumentException("会话不存在或更新失败: " + conversationId);
        }
    }

    @Override
    public void archiveConversation(String conversationId) {
        LambdaUpdateWrapper<ChatConversation> updateWrapper = new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .set(ChatConversation::getStatus, ConversationStatus.ARCHIVED);
        boolean updated = update(updateWrapper);
        if (!updated) {
            throw new IllegalArgumentException("会话不存在或归档失败: " + conversationId);
        }
        log.info("归档会话成功, conversationId={}", conversationId);
    }

    @Override
    public void deleteConversation(String conversationId) {
        LambdaQueryWrapper<ChatConversation> queryWrapper = new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId);
        boolean removed = remove(queryWrapper);
        if (!removed) {
            throw new IllegalArgumentException("会话不存在或删除失败: " + conversationId);
        }
        log.info("删除会话成功（逻辑删除）, conversationId={}", conversationId);
    }
}
