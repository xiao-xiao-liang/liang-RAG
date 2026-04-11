package com.liang.rag.document.splitter;

import com.liang.rag.common.enums.SplitType;
import com.liang.rag.document.entity.DocumentSplitParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * 文档切分器工厂
 * <p>
 * 根据 {@link DocumentSplitParam} 中的参数选择对应的文档切分策略。
 * </p>
 */
@Slf4j
public final class DocumentSplitterFactory {

    private DocumentSplitterFactory() {
        // 工具类禁止实例化
    }

    /**
     * 根据参数获取文档切分器
     *
     * @param param 切分参数，为空时使用默认的 Markdown 标题切分策略
     * @return 文档切分器
     */
    public static DocumentTransformer getInstance(DocumentSplitParam param) {
        if (param == null || param.getSplitType() == null) {
            return new ParentMarkdownSplitter(1000, 100);
        }

        SplitType splitType;
        try {
            splitType = SplitType.valueOf(param.getSplitType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无法识别的切分类型: {}, 使用默认 LENGTH 策略", param.getSplitType());
            splitType = SplitType.LENGTH;
        }

        int chunkSize = param.getChunkSize() != null ? param.getChunkSize() : 1000;
        int overlap = param.getOverlap() != null ? param.getOverlap() : 100;

        return switch (splitType) {
            case TITLE -> new ParentMarkdownSplitter(chunkSize, overlap);
            case LENGTH -> new TokenTextSplitter(chunkSize, chunkSize, overlap, 10000, true);
            case REGEX -> {
                log.warn("REGEX 切分策略尚未实现, 降级为 LENGTH 策略");
                yield new TokenTextSplitter(chunkSize, chunkSize, overlap, 10000, true);
            }
            case SMART -> {
                log.warn("SMART 切分策略尚未实现, 降级为 LENGTH 策略");
                yield new TokenTextSplitter(chunkSize, chunkSize, overlap, 10000, true);
            }
            case SEPARATOR -> {
                log.warn("SEPARATOR 切分策略尚未实现, 降级为 LENGTH 策略");
                yield new TokenTextSplitter(chunkSize, chunkSize, overlap, 10000, true);
            }
        };
    }
}
