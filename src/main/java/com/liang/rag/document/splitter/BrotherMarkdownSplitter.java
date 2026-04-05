package com.liang.rag.document.splitter;

import java.util.*;
import java.util.stream.Collectors;

import static com.liang.rag.common.constant.MetadataKeyConstant.*;

/**
 * Markdown 文档分割器（兄弟分段模式）— 适配 Spring AI DocumentTransformer
 * <p>
 * 基于 Markdown 标题层级切分文档，并建立父子标题关系。
 * 当某个片段超过 {@code chunkSize} 时，生成同组兄弟片段：
 * 共享相同的 {@code brotherChunkId}，并通过 {@code brotherChunkIndex}/{@code brotherChunkTotal} 支持按序拼接。
 * </p>
 */
public class BrotherMarkdownSplitter extends AbstractMarkdownSplitter {

    /** 是否构建父子标题关系（在聚合阶段向前查找父标题） */
    private final boolean parentChildModel;

    /**
     * 快速构造
     */
    public BrotherMarkdownSplitter(int chunkSize, int overlap) {
        this(DEFAULT_HEADERS_TO_SPLIT, true, false, true, chunkSize, overlap);
    }

    /**
     * 完整构造
     *
     * @param headersToSplitOn 标题分割映射（key=标题标记如"##"，value=元数据键名）
     * @param returnEachLine   是否逐行返回（false 时聚合相同元数据的行）
     * @param stripHeaders     是否在结果中移除标题行
     * @param parentChildModel 是否在聚合阶段构建父子标题关系
     * @param chunkSize        每个分片的最大字符数（超出则二次切割），0 表示不限制
     * @param overlap          相邻分片之间的重叠字符数
     */
    public BrotherMarkdownSplitter(Map<String, String> headersToSplitOn,
                                                     boolean returnEachLine, boolean stripHeaders,
                                                     boolean parentChildModel,
                                                     int chunkSize, int overlap) {
        super(headersToSplitOn, returnEachLine, stripHeaders, chunkSize, overlap);
        this.parentChildModel = parentChildModel;
    }

    /**
     * 聚合行为分块，并处理父子标题关系
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

        // 处理父子标题关系
        if (parentChildModel) {
            try {
                for (int i = 0; i < aggregated.size(); i++) {
                    Map<String, Object> meta = aggregated.get(i).metadata();
                    Integer headerLevel = (Integer) meta.get(HEADER_LEVEL);
                    if (headerLevel == null || headerLevel == 1) continue;

                    // 向前查找父标题
                    for (int j = i - 1; j >= 0; j--) {
                        Map<String, Object> prevMeta = aggregated.get(j).metadata();
                        Integer prevLevel = (Integer) prevMeta.get(HEADER_LEVEL);
                        if (prevLevel != null && prevLevel < headerLevel) {
                            meta.put(PARENT_CHUNK_ID, prevMeta.get(CHUNK_ID));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // 父子关系构建失败不影响主流程
            }
        }

        return aggregated.stream()
                .map(chunk -> new DocumentChunk(chunk.content(), chunk.metadata()))
                .collect(Collectors.toList());
    }

    /**
     * 对超出 chunkSize 的分片进行二次切割（兄弟模式）
     * <p>同组兄弟片段共享 brotherChunkId，并记录 index/total 用于按序拼接。</p>
     */
    @Override
    protected List<DocumentChunk> splitByChunkSize(List<DocumentChunk> segments) {
        List<DocumentChunk> result = new ArrayList<>();
        for (DocumentChunk segment : segments) {
            String content = segment.content();
            if (content.length() <= chunkSize) {
                result.add(segment);
            } else {
                String brotherChunkId = UUID.randomUUID().toString();
                List<DocumentChunk> subChunks = new ArrayList<>();

                int start = 0;
                while (start < content.length()) {
                    int end = Math.min(start + chunkSize, content.length());
                    String subContent = content.substring(start, end);

                    Map<String, Object> subMeta = new HashMap<>(segment.metadata());
                    subMeta.put(CHUNK_ID, UUID.randomUUID().toString());
                    subMeta.put(BROTHER_CHUNK_ID, brotherChunkId);
                    subChunks.add(new DocumentChunk(subContent, subMeta));

                    if (end == content.length()) break;
                    start = end - Math.min(overlap, end);
                }

                // 回填 index 和 total
                int total = subChunks.size();
                for (int i = 0; i < total; i++) {
                    subChunks.get(i).metadata().put(BROTHER_CHUNK_INDEX, i + 1);
                    subChunks.get(i).metadata().put(BROTHER_CHUNK_TOTAL, total);
                }

                result.addAll(subChunks);
            }
        }
        return result;
    }
}
