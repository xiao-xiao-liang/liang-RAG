package com.liang.rag.parser;

import com.liang.rag.parser.config.DocumentProcessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图片描述生成服务
 * <p>
 * 调用 qwen3-vl-plus 多模态大模型，为图片生成自然语言描述文本。
 * 生成的描述将填入 Markdown 图片标签的 alt text 中，
 * 提升文档的可读性和语义检索效果。
 * </p>
 * <p>
 * 图片通过本地文件读取后以字节流方式发送给 LLM，
 * 无需图片可被外网访问（解决 MinIO 在内网场景下 LLM 无法获取图片的问题）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDescriptionService {

    private final ChatModel chatModel;
    private final DocumentProcessProperties processProperties;

    /**
     * 读取本地图片并调用 qwen3-vl-plus 生成自然语言描述
     *
     * @param localImagePath 图片的本地文件路径
     * @param fileName       图片文件名（用于 MIME 推断和降级描述）
     * @return 图片的自然语言描述文本
     */
    public String generateDescription(Path localImagePath, String fileName) {
        try {
            String visionModel = processProperties.getVisionModel();
            log.info("调用 {} 生成图片描述, fileName: {}", visionModel, fileName);

            byte[] imageBytes = Files.readAllBytes(localImagePath);
            MimeType mimeType = guessImageMimeType(fileName);

            ChatClient chatClient = ChatClient.builder(chatModel).build();

            String description = chatClient.prompt()
                    .options(ChatOptions.builder()
                            .model(visionModel)
                            .build())
                    .user(userSpec -> userSpec
                            .text("请用简洁的中文描述这张图片的内容，不超过50个字。仅输出描述文字，不要有任何前缀或额外内容。")
                            .media(mimeType, new ByteArrayResource(imageBytes)))
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
     * 根据文件扩展名推断图片的 MIME 类型
     */
    private MimeType guessImageMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if (lower.endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        if (lower.endsWith(".svg")) {
            return MimeTypeUtils.parseMimeType("image/svg+xml");
        }
        return MimeTypeUtils.APPLICATION_OCTET_STREAM;
    }
}
