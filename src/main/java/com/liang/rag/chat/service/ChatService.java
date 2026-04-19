package com.liang.rag.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.rag.chat.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式对话编排服务
 * <p>
 * 负责编排 LLM 调用、消息持久化和会话管理的完整流程。
 * 将业务逻辑从 Controller 中解耦，Controller 仅负责参数校验和路由。
 * </p>
 */
@Slf4j
@Service
public class ChatService {

    /**
     * 主对话模型（高质量）
     */
    private final ChatModel chatModel;

    /**
     * 轻量模型（低成本，用于标题生成等简单任务）
     */
    private final ChatModel lightChatModel;

    /**
     * JSON 序列化器，用于 SSE token 编码
     */
    private final ObjectMapper objectMapper;

    private final ChatMessageService messageService;
    private final ChatConversationService conversationService;

    public ChatService(
            ChatModel chatModel,
            @Qualifier("lightChatModel") ChatModel lightChatModel,
            ChatMessageService messageService,
            ChatConversationService conversationService,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.lightChatModel = lightChatModel;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = "你是一个智能助手，请根据用户的问题进行回答。";

    /**
     * 历史消息上下文窗口大小
     */
    private static final int CONTEXT_WINDOW_SIZE = 20;

    /**
     * 流式对话
     * <p>
     * 完整编排流程：创建/复用会话 → 保存用户消息 → 构建上下文 → 流式调用 LLM → 异步保存 AI 回复 → 推送结束事件
     * </p>
     *
     * @param userId         用户ID
     * @param content        用户消息内容
     * @param conversationId 会话ID（可为空，为空则创建新会话）
     * @return SSE 流，逐字推送 AI 回复，结尾推送 [DONE]:conversationId
     */
    public Flux<String> streamChat(String userId, String content, String conversationId) {
        // 1. 处理会话：没有 conversationId 则创建新会话
        final String finalConversationId;
        if (conversationId == null || conversationId.isBlank()) {
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            finalConversationId = conversationService.createConversation(userId, tempTitle);
            log.info("创建新会话, conversationId={}, tempTitle={}", finalConversationId, tempTitle);

            // 异步：使用虚拟线程调用 LLM 生成摘要标题，完成后回写数据库
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    String aiTitle = generateTitle(content);
                    conversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("异步标题更新完成, conversationId={}, title={}", finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.warn("异步标题生成失败, 保留临时标题, conversationId={}", finalConversationId, e);
                }
            });
        } else {
            finalConversationId = conversationId;
        }

        // 2. 保存用户消息
        messageService.saveUserMessage(finalConversationId, content);

        // 3. 构建历史消息上下文
        List<Message> historyMessages = buildHistoryContext(finalConversationId);

        // 4. 调用 Spring AI ChatClient 流式对话
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        // 使用 StringBuilder 在流式过程中收集完整回复
        StringBuilder fullResponse = new StringBuilder();

        return Flux.just("[START]:" + finalConversationId)
                .concatWith(chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .messages(historyMessages)
                        .user(content)
                        .stream()
                        .content()
                        // 收集每个 token（原始值，用于持久化）
                        .doOnNext(fullResponse::append)
                        // JSON 编码每个 token，避免 SSE 协议吞掉换行符和空格
                        .map(this::encodeTokenForSse)
                        // 流正常结束后，异步保存 AI 回复到数据库
                        .doOnComplete(() -> {
                            Schedulers.boundedElastic().schedule(() -> {
                                try {
                                    String modelName = chatModel.getDefaultOptions().getModel();
                                    messageService.saveAssistantMessage(
                                            finalConversationId,
                                            fullResponse.toString(),
                                            modelName,
                                            null,
                                            null,
                                            null
                                    );
                                } catch (Exception e) {
                                    log.error("保存AI回复失败, conversationId={}", finalConversationId, e);
                                }
                            });
                        })
                        // 流式异常降级：返回友好提示，不直接断开连接
                        .onErrorResume(e -> {
                            log.error("流式对话异常, conversationId={}", finalConversationId, e);
                            return Flux.just(encodeTokenForSse("抱歉，系统繁忙，请稍后再试。"));
                        })
                        // 追加结束事件标志
                        .concatWith(Flux.just("[DONE]:" + finalConversationId)));
    }

    /**
     * 将 token 编码为 JSON 字符串格式，用于 SSE 传输。
     * <p>
     * SSE 协议中 data 字段无法直接传输换行符（\n 会被识别为事件边界），
     * 且 Spring WebFlux 不会在 data: 后添加分隔空格，导致空格 token 被前端按规范误删。
     * 使用 JSON 编码可以完美解决以上两个问题。
     * </p>
     *
     * @param token 原始 token 文本
     * @return JSON 编码后的字符串（如 "hello" → "\"hello\""）
     */
    private String encodeTokenForSse(String token) {
        try {
            return objectMapper.writeValueAsString(token);
        } catch (JsonProcessingException e) {
            log.warn("Token JSON编码失败, 使用原始值: {}", token, e);
            return token;
        }
    }

    /**
     * 调用 LLM 生成会话标题摘要
     *
     * @param content 用户首条消息内容
     * @return AI 生成的标题
     */
    private String generateTitle(String content) {
        ChatClient chatClient = ChatClient.builder(lightChatModel).build();
        String title = chatClient.prompt()
                .system("你是一个标题生成助手。请根据用户的消息，生成一个简洁的中文标题（不超过15个字）。仅输出标题文字，不要有任何前缀、标点或额外内容。")
                .user(content)
                .call()
                .content();
        return (title != null && !title.isBlank()) ? title.trim() : content.substring(0, Math.min(content.length(), 15));
    }

    /**
     * 构建历史消息上下文
     * <p>
     * 将 DB 中的历史消息转换为 Spring AI 的 Message 对象列表，
     * 用于填充 ChatClient 的上下文窗口。
     * </p>
     *
     * @param conversationId 会话ID
     * @return Spring AI Message 列表
     */
    private List<Message> buildHistoryContext(String conversationId) {
        List<ChatMessage> recentMessages = messageService.getRecentMessages(conversationId, CONTEXT_WINDOW_SIZE);
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : recentMessages) {
            switch (msg.getType()) {
                case "USER" -> messages.add(new UserMessage(msg.getContent()));
                case "ASSISTANT" -> messages.add(new AssistantMessage(msg.getContent()));
                default -> log.warn("未知消息类型: {}", msg.getType());
            }
        }
        return messages;
    }
}
