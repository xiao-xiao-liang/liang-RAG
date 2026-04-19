package com.liang.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流式对话请求 DTO
 */
@Data
public class ChatSendRequest {

    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * 用户消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * 会话ID（可选，不传则自动创建新会话）
     */
    private String conversationId;
}
