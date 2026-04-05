package com.liang.rag.rag.splitter;

import lombok.Setter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.*;
import java.util.stream.Collectors;

import static com.liang.rag.common.constant.MetadataKeyConstant.*;

/**
 * Markdown 文档分割器抽象基类 — 适配 Spring AI DocumentTransformer
 * <p>
 * 基于 Markdown 标题层级（# ~ ######）对文档进行结构化切分。
 * 核心分割逻辑（标题检测、代码块处理、元数据管理）统一在此基类实现，
 * 子类仅需实现二次切割策略 {@link #splitByChunkSize} 和聚合策略 {@link #aggregateLinesToChunks}。
 * </p>
 *
 * <p>采用模板方法设计模式，处理流程：
 * {@code apply()} → {@code splitDocument()} → {@code splitWithMetadata()}
 * → {@code aggregateLinesToChunks()} → {@code splitByChunkSize()}</p>
 *
 * @see ParentMarkdownSplitter 父子分段模式
 * @see BrotherMarkdownSplitter 兄弟分段模式
 */
public abstract class AbstractMarkdownSplitter implements DocumentTransformer {

    /** 默认标题层级映射（# ~ ######） */
    protected static final Map<String, String> DEFAULT_HEADERS_TO_SPLIT = new LinkedHashMap<>();

    static {
        DEFAULT_HEADERS_TO_SPLIT.put("#", "title");
        DEFAULT_HEADERS_TO_SPLIT.put("##", "subtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("###", "subsubtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("####", "subsubsubtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("#####", "subsubsubsubtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("######", "subsubsubsubsubtitle");
    }

    /** 需要分割的标题列表，按标题标记长度倒序排列 */
    protected final List<Map.Entry<String, String>> headersToSplitOn;

    /** 是否按行返回结果 */
    protected final boolean returnEachLine;

    /** 是否剥离标题行本身 */
    protected final boolean stripHeaders;

    /** 每个分片的最大字符数，0 表示不限制 */
    protected final int chunkSize;

    /** 相邻分片之间的重叠字符数 */
    protected final int overlap;

    /**
     * 完整构造
     *
     * @param headersToSplitOn 标题分割映射（key=标题标记如"##"，value=元数据键名）
     * @param returnEachLine   是否逐行返回（false 时聚合相同元数据的行）
     * @param stripHeaders     是否在结果中移除标题行
     * @param chunkSize        每个分片的最大字符数（超出则二次切割），0 表示不限制
     * @param overlap          相邻分片之间的重叠字符数
     */
    protected AbstractMarkdownSplitter(Map<String, String> headersToSplitOn,
                                                        boolean returnEachLine, boolean stripHeaders,
                                                        int chunkSize, int overlap) {
        this.headersToSplitOn = headersToSplitOn.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getKey().length()))
                .collect(Collectors.toList());
        this.returnEachLine = returnEachLine;
        this.stripHeaders = stripHeaders;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document document : documents) {
            result.addAll(splitDocument(document));
        }
        return result;
    }

    /**
     * 对单个文档进行切分
     */
    private List<Document> splitDocument(Document document) {
        // 移除空行
        String text = Arrays.stream(document.getText().split("\n"))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.joining("\n"));

        Map<String, Object> baseMetadata = new HashMap<>(document.getMetadata());
        List<DocumentChunk> chunks = splitWithMetadata(text, baseMetadata);

        return chunks.stream()
                .map(chunk -> new Document(chunk.content(), chunk.metadata()))
                .collect(Collectors.toList());
    }

    /**
     * 核心分割逻辑（模板方法骨架）
     * <p>
     * 按行扫描 Markdown 文本，处理代码块、识别标题层级、维护标题栈和元数据，
     * 最终调用子类的 {@link #aggregateLinesToChunks} 和 {@link #splitByChunkSize} 完成差异化处理。
     * </p>
     */
    private List<DocumentChunk> splitWithMetadata(String text, Map<String, Object> baseMetadata) {
        String[] lines = text.split("\n");
        List<Line> linesWithMetadata = new ArrayList<>();
        List<String> currentContent = new ArrayList<>();
        Map<String, Object> currentMetadata = new HashMap<>(baseMetadata);
        List<Header> headerStack = new ArrayList<>();
        Map<String, Object> initialMetadata = new HashMap<>(baseMetadata);

        boolean inCodeBlock = false;
        String openingFence = "";

        for (String line : lines) {
            String strippedLine = line.trim();

            // 处理代码块标记
            if (!inCodeBlock) {
                if (strippedLine.startsWith("```")) {
                    inCodeBlock = true;
                    openingFence = "```";
                } else if (strippedLine.startsWith("~~~")) {
                    inCodeBlock = true;
                    openingFence = "~~~";
                }
            } else {
                if (strippedLine.startsWith(openingFence)) {
                    inCodeBlock = false;
                    openingFence = "";
                }
            }

            // 代码块内容直接添加，不做标题检测
            if (inCodeBlock) {
                currentContent.add(strippedLine);
                continue;
            }

            // 标题检测
            boolean headerFound = false;
            for (Map.Entry<String, String> header : headersToSplitOn) {
                String sep = header.getKey();
                String name = header.getValue();

                if (isHeaderLine(strippedLine, sep)) {
                    if (name != null) {
                        int currentHeaderLevel = sep.length();

                        // 维护标题栈：移除所有级别 >= 当前级别的标题
                        while (!headerStack.isEmpty() && headerStack.getLast().level() >= currentHeaderLevel) {
                            Header popped = headerStack.removeLast();
                            initialMetadata.remove(popped.name());
                        }

                        // 入栈并更新元数据
                        Header headerType = new Header(currentHeaderLevel, name,
                                strippedLine.substring(sep.length()).trim());
                        headerStack.add(headerType);
                        initialMetadata.put(name, headerType.data());
                        initialMetadata.put(HEADER_LEVEL, currentHeaderLevel);
                        initialMetadata.put(CHUNK_ID, UUID.randomUUID().toString());
                    }

                    // 保存之前累积的内容
                    if (!currentContent.isEmpty()) {
                        linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
                        currentContent.clear();
                    }

                    if (!stripHeaders)
                        currentContent.add(strippedLine);

                    headerFound = true;
                    break;
                }
            }

            if (!headerFound) {
                if (!strippedLine.isEmpty()) {
                    currentContent.add(strippedLine);
                } else if (!currentContent.isEmpty()) {
                    linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
                    currentContent.clear();
                }
            }

            currentMetadata = new HashMap<>(initialMetadata);
        }

        // 处理最后累积的内容
        if (!currentContent.isEmpty()) {
            linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
        }

        // 聚合或逐行返回（调用子类策略）
        List<DocumentChunk> segments;
        if (!returnEachLine) {
            segments = aggregateLinesToChunks(linesWithMetadata);
        } else {
            segments = linesWithMetadata.stream()
                    .map(l -> new DocumentChunk(l.content(), l.metadata()))
                    .collect(Collectors.toList());
        }

        // 二次切割（调用子类策略）
        if (chunkSize > 0)
            segments = splitByChunkSize(segments);

        return segments;
    }

    /**
     * 判断是否为有效的标题行
     */
    private boolean isHeaderLine(String line, String headerMark) {
        return line.startsWith(headerMark) &&
                (line.length() == headerMark.length() || line.charAt(headerMark.length()) == ' ');
    }

    /**
     * 聚合具有相同元数据的行为文档片段
     * <p>子类可在此阶段添加额外的元数据关系（如父子标题关联）。</p>
     *
     * @param lines 带有元数据的文本行列表
     * @return 聚合后的文档片段列表
     */
    protected abstract List<DocumentChunk> aggregateLinesToChunks(List<Line> lines);

    /**
     * 对超出 chunkSize 的分片进行二次切割
     * <p>子类定义具体的切割策略（兄弟模式 / 父子模式）。</p>
     *
     * @param segments 一次切割后的文档片段列表
     * @return 二次切割后的文档片段列表
     */
    protected abstract List<DocumentChunk> splitByChunkSize(List<DocumentChunk> segments);

    /** 带有元数据的文本行（可变内容） */
    protected static class Line {
        @Setter
        private String content;
        private final Map<String, Object> metadata;

        protected Line(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = new HashMap<>(metadata);
        }

        public String content() { return content; }
        public Map<String, Object> metadata() { return metadata; }
    }

    /** Markdown 标题信息 */
    protected record Header(int level, String name, String data) {}

    /** 带有元数据的文档片段（不可变） */
    protected record DocumentChunk(String content, Map<String, Object> metadata) {
        protected DocumentChunk(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = new HashMap<>(metadata);
        }
    }
}
