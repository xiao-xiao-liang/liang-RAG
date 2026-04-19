package com.liang.rag.chat.controller;

import com.liang.rag.chat.dto.ChatSendRequest;
import com.liang.rag.chat.entity.ChatConversation;
import com.liang.rag.chat.entity.ChatMessage;
import com.liang.rag.chat.service.ChatConversationService;
import com.liang.rag.chat.service.ChatMessageService;
import com.liang.rag.chat.service.ChatService;
import com.liang.rag.common.convention.result.Result;
import com.liang.rag.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式会话控制器
 * <p>
 * 提供流式对话（SSE）、会话管理和消息查询等 RESTful API。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageService messageService;
    private final ChatConversationService conversationService;

    /**
     * 流式对话
     * <p>
     * 通过 SSE（Server-Sent Events）逐字推送 AI 回复。
     * 若未传入 conversationId，将自动创建新会话。
     * 流结束前推送一条 [DONE]:conversationId 事件，供前端获取会话标识。
     * </p>
     *
     * @param request 对话请求（userId、content 必填，conversationId 可选）
     * @return SSE 流
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> send(@Valid @RequestBody ChatSendRequest request) {
        log.info("收到对话请求, userId={}, conversationId={}", request.getUserId(), request.getConversationId());
        return chatService.streamChat(request.getUserId(), request.getContent(), request.getConversationId());
    }

    /**
     * 查询指定用户的会话列表
     *
     * @param userId 用户ID
     * @return 会话列表（按创建时间倒序）
     */
    @GetMapping("/conversations")
    public Result<List<ChatConversation>> listConversations(@RequestParam String userId) {
        return Results.success(conversationService.getConversationsByUserId(userId));
    }

    /**
     * 查询指定会话的消息列表
     *
     * @param conversationId 会话ID
     * @return 消息列表（按创建时间正序）
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<ChatMessage>> listMessages(@PathVariable String conversationId) {
        return Results.success(messageService.getByConversationId(conversationId));
    }

    /**
     * 删除会话及其所有消息（逻辑删除）
     *
     * @param conversationId 会话ID
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable String conversationId) {
        messageService.deleteByConversationId(conversationId);
        conversationService.deleteConversation(conversationId);
        return Results.success();
    }

    /**
     * 查询会话标题
     * <p>
     * 轻量级接口，专门用于前端在新会话创建后异步拉取 AI 生成的标题。
     * 避免为了一个 title 全量拉取整个会话列表。
     * </p>
     *
     * @param conversationId 会话ID
     * @return 当前标题
     */
    @GetMapping("/conversations/{conversationId}/title")
    public Result<String> getTitle(@PathVariable String conversationId) {
        ChatConversation conversation = conversationService.getConversation(conversationId);
        return Results.success(conversation != null ? conversation.getTitle() : "");
    }

    /**
     * 更新会话标题
     *
     * @param conversationId 会话ID
     * @param title          新标题
     */
    @PutMapping("/conversations/{conversationId}/title")
    public Result<Void> updateTitle(@PathVariable String conversationId, @RequestParam String title) {
        conversationService.updateTitle(conversationId, title);
        return Results.success();
    }
}
