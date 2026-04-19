package com.liang.rag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.rag.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话消息表 Mapper 接口
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
