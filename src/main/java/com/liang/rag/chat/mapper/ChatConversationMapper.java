package com.liang.rag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.rag.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI会话表 Mapper 接口
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
