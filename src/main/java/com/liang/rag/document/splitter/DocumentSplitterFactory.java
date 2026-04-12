package com.liang.rag.document.splitter;

import com.liang.rag.common.enums.SplitType;
import com.liang.rag.document.entity.DocumentSplitParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * 文档切分器工厂
 * <p>
 * 根据 {@link DocumentSplitParam} 中的参数选择对应的文档切分策略，
 * 与 know-engine 的 {@code DocumentSplitterFactory} 保持功能对齐。
 * </p>
 * <p>
 * 策略对照表（know-engine LangChain4j → liang-RAG Spring AI）：
 * <ul>
 *     <li>TITLE → {@link ParentMarkdownSplitter}（Markdown 标题切分 + 父子分段）</li>
 *     <li>LENGTH → {@link TokenTextSplitter}（基于 Token 的长度切分，比 know-engine 的 WordSplitter 更适合 LLM 场景）</li>
 *     <li>SEPARATOR → {@link RegexTextSplitter#fromSeparator}（按固定分隔符切分）</li>
 *     <li>REGEX → {@link RegexTextSplitter}（按正则表达式切分）</li>
 *     <li>SMART → {@link ParentMarkdownSplitter}（Markdown 标题切分，overlap 自动取 chunkSize 的 10%）</li>
 * </ul>
 * </p>
 */
@Slf4j
public final class DocumentSplitterFactory {

    /**
     * 单个文档最大输入 Token 数上限，超出则截断
     */
    private static final int MAX_INPUT_SIZE = 10000;

    private DocumentSplitterFactory() {
        // 工具类禁止实例化
    }

    /**
     * 创建基于 Token 的长度切分器
     */
    private static TokenTextSplitter createTokenSplitter(int chunkSize, int overlap) {
        return new TokenTextSplitter(chunkSize, chunkSize, overlap, MAX_INPUT_SIZE, true);
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
            case LENGTH -> createTokenSplitter(chunkSize, overlap);
            case SEPARATOR -> {
                String separator = param.getSeparator();
                if (separator == null || separator.isEmpty()) {
                    log.warn("SEPARATOR 策略未指定分隔符, 降级为 LENGTH 策略");
                    yield createTokenSplitter(chunkSize, overlap);
                }
                yield RegexTextSplitter.fromSeparator(separator, chunkSize, overlap);
            }
            case REGEX -> {
                String regex = param.getRegex();
                if (regex == null || regex.isBlank()) {
                    log.warn("REGEX 策略未指定正则表达式, 降级为 LENGTH 策略");
                    yield createTokenSplitter(chunkSize, overlap);
                }
                yield new RegexTextSplitter(regex, "\n\n", chunkSize, overlap);
            }
            case SMART -> {
                // 对齐 know-engine: overlap 自动取 chunkSize 的 10%
                int smartOverlap = (int) (chunkSize * 0.1);
                yield new ParentMarkdownSplitter(chunkSize, smartOverlap);
            }
        };
    }
}
