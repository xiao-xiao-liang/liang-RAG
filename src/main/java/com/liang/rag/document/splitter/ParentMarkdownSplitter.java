package com.liang.rag.document.splitter;

import com.liang.rag.infra.snowflake.SnowflakeIdGenerator;

import java.util.*;
import java.util.stream.Collectors;

import static com.liang.rag.common.constant.MetadataKeyConstant.*;

/**
 * Markdown 文档分割器（父子分段模式）— 适配 Spring AI DocumentTransformer
 * <p>
 * 基于 Markdown 标题层级（# ~ ######）对文档进行结构化切分。
 * 当某个片段超过 {@code chunkSize} 时，保留完整的父片段（标记 skipEmbedding=1），
 * 同时生成拆分后的子片段，子片段通过 {@code parentChunkId} 关联父片段。
 * </p>
 *
 * <p>原始算法来源: <a href="https://github.com/langchain4j/langchain4j/issues/574">langchain4j #574</a></p>
 */
public class ParentMarkdownSplitter extends AbstractMarkdownSplitter {

    /**
     * 快速构造（指定 chunkSize 和 overlap，使用默认标题层级）
     */
    public ParentMarkdownSplitter(int chunkSize, int overlap) {
        this(DEFAULT_HEADERS_TO_SPLIT, true, false, chunkSize, overlap);
    }

    /**
     * 完整构造
     *
     * @param headersToSplitOn 标题分割映射（key=标题标记如"##"，value=元数据键名）
     * @param returnEachLine   是否逐行返回（false 时聚合相同元数据的行）
     * @param stripHeaders     是否在结果中移除标题行
     * @param chunkSize        每个分片的最大字符数（超出则二次切割），0 表示不限制
     * @param overlap          相邻分片之间的重叠字符数
     */
    public ParentMarkdownSplitter(Map<String, String> headersToSplitOn,
                                                    boolean returnEachLine, boolean stripHeaders,
                                                    int chunkSize, int overlap) {
        super(headersToSplitOn, returnEachLine, stripHeaders, chunkSize, overlap);
    }

    /**
     * 聚合具有相同元数据的行
     */
    @Override
    protected List<DocumentChunk> aggregateLinesToChunks(List<Line> lines) {
        List<Line> aggregated = new ArrayList<>();
        for (Line line : lines) {
            if (!aggregated.isEmpty() &&
                    aggregated.getLast().metadata().equals(line.metadata())) {
                Line last = aggregated.getLast();
                last.setContent(last.content() + "  \n" + line.content());
            } else if (!aggregated.isEmpty() &&
                    aggregated.getLast().metadata().size() < line.metadata().size() &&
                    aggregated.getLast().content().split("\n")[aggregated.getLast().content().split("\n").length - 1].startsWith("#") &&
                    !stripHeaders) {
                Line last = aggregated.getLast();
                last.setContent(last.content() + "  \n" + line.content());
            } else {
                aggregated.add(line);
            }
        }

        return aggregated.stream()
                .map(chunk -> new DocumentChunk(chunk.content(), chunk.metadata()))
                .collect(Collectors.toList());
    }

    /**
     * 对超出 chunkSize 的分片进行二次切割（父子模式）
     * <p>
     * 超长片段保留为父片段（skipEmbedding=1），同时生成多个子片段。
     * 子片段通过 parentChunkId 关联父片段。
     * </p>
     */
    @Override
    protected List<DocumentChunk> splitByChunkSize(List<DocumentChunk> segments) {
        List<DocumentChunk> result = new ArrayList<>();
        for (DocumentChunk segment : segments) {
            String content = segment.content();
            if (content.length() <= chunkSize) {
                result.add(segment);
            } else {
                // 保留完整父片段，标记跳过 Embedding
                Map<String, Object> parentMeta = new HashMap<>(segment.metadata());
                String parentChunkId = SnowflakeIdGenerator.getInstance().nextIdStr();
                parentMeta.put(CHUNK_ID, parentChunkId);
                parentMeta.put(SKIP_EMBEDDING, 1);
                result.add(new DocumentChunk(content, parentMeta));

                // 生成子片段
                int start = 0;
                while (start < content.length()) {
                    int end = Math.min(start + chunkSize, content.length());
                    String subContent = content.substring(start, end);

                    Map<String, Object> subMeta = new HashMap<>(segment.metadata());
                    subMeta.put(CHUNK_ID, SnowflakeIdGenerator.getInstance().nextIdStr());
                    subMeta.put(PARENT_CHUNK_ID, parentChunkId);

                    result.add(new DocumentChunk(subContent, subMeta));

                    if (end == content.length()) break;
                    start = end - Math.min(overlap, end);
                }
            }
        }
        return result;
    }
}
