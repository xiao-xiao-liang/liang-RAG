package com.liang.rag.parser;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.liang.rag.parser.config.DocumentProcessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.net.URI;

/**
 * 图片描述生成服务
 * <p>
 * 调用 qwen3-vl-plus 多模态大模型，为图片生成自然语言描述文本。
 * 生成的描述将填入 Markdown 图片标签的 alt text 中，
 * 提升文档的可读性和语义检索效果。
 * </p>
 * <p>
 * 图片通过 MinIO URL 传给 LLM（需确保 DashScope API 能访问该 URL）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDescriptionService {

    private final ChatModel chatModel;
    private final DocumentProcessProperties processProperties;

    /**
     * 调用 qwen3-vl-plus 根据图片 URL 生成自然语言描述
     *
     * @param imageUrl 图片的可访问 URL（MinIO 地址）
     * @param fileName 图片文件名（用于降级描述和 MIME 推断）
     * @return 图片的自然语言描述文本
     */
    public String generateDescription(String imageUrl, String fileName) {
        try {
            String visionModel = processProperties.getVisionModel();
            log.info("调用 {} 生成图片描述, fileName: {}, imageUrl: {}", visionModel, fileName, imageUrl);

            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 使用 DashScopeChatOptions 并开启 multiModel，否则 API 会走纯文本端点报 url error
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .model(visionModel)
                    .multiModel(true)
                    .build();

            String description = chatClient.prompt()
                    .options(options)
                    .user(userSpec -> {
                        try {
                            userSpec
                                    .text("请用简洁的中文描述这张图片的内容，不超过50个字。仅输出描述文字，不要有任何前缀或额外内容。")
                                    .media(guessImageMimeType(fileName), URI.create(imageUrl).toURL());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .call()
                    .content();

            log.info("图片描述生成成功, fileName: {}, description: {}", fileName, description);
            return (description != null && !description.isBlank()) ? description.trim() : "图片：" + fileName;
        } catch (Exception e) {
            log.warn("图片描述生成失败, fileName: {}, 降级使用文件名", fileName, e);
            return "图片：" + fileName;
        }
    }

    /**
     * 根据文件后缀推断 MIME 类型
     */
    private MimeType guessImageMimeType(String fileName) {
        if (fileName == null) return MimeTypeUtils.APPLICATION_OCTET_STREAM;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return MimeTypeUtils.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MimeTypeUtils.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MimeTypeUtils.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MimeTypeUtils.parseMimeType("image/webp");
        if (lower.endsWith(".svg")) return MimeTypeUtils.parseMimeType("image/svg+xml");
        return MimeTypeUtils.APPLICATION_OCTET_STREAM;
    }
}

