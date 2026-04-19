package com.liang.rag.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多模型配置
 * <p>
 * 由于 spring-ai-alibaba-starter 不暴露 DashScopeApi Bean，我们手动创建。
 * </p>
 */
@Configuration
public class ChatModelConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /**
     * 共享的 DashScope API 客户端
     * 使用 new DashScopeApi(apiKey) 可以正确初始化默认的 text-generation 和 embeddings endpoints。
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    /**
     * 主对话模型（高质量）
     */
    @Primary
    @Bean("chatModel")
    public ChatModel chatModel(DashScopeApi dashScopeApi) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model("qwen-max-latest")
                .temperature(0.7)
                .enableThinking(false)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 轻量模型（低成本）
     */
    @Bean("lightChatModel")
    public ChatModel lightChatModel(DashScopeApi dashScopeApi) {
        // 【注意】百炼平台上的 qwen3.5-flash 属于多模态（视觉语言）架构模型。
        // 调用多模态模型时，必须显式声明 .multiModel(true)，底层 API 才会走 /multimodal-generation 路由，否则会报 url error。
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model("qwen3.5-flash")
                .multiModel(true)
                .temperature(0.3)
                .enableThinking(false)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }
}
