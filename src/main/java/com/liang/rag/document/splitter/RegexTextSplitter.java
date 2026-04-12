package com.liang.rag.document.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于正则表达式的文档切分器 — 适配 Spring AI DocumentTransformer
 * <p>
 * 对标 LangChain4j 的 {@code DocumentByRegexSplitter}，行为逻辑：
 * <ol>
 *     <li>使用正则表达式将文本切成多个 part</li>
 *     <li>将相邻的 part 用 {@code joinDelimiter} 合并，直到字符数接近 {@code maxSegmentSize}</li>
 *     <li>如果某个单独的 part 仍然超过 {@code maxSegmentSize}，则按字符边界进行二次切分</li>
 *     <li>相邻 segment 之间保留 {@code overlapSize} 个字符的重叠，保持上下文连贯</li>
 * </ol>
 * </p>
 * <p>
 * 同时支持 SEPARATOR（固定分隔符）和 REGEX（正则表达式）两种切分模式：
 * <ul>
 *     <li>SEPARATOR：调用 {@link #fromSeparator(String, int, int)} 工厂方法，内部自动 {@code Pattern.quote} 转义</li>
 *     <li>REGEX：调用构造函数直接传入正则</li>
 * </ul>
 * </p>
 */
@Slf4j
public class RegexTextSplitter implements DocumentTransformer {

    /**
     * 用于切分文本的正则表达式
     */
    private final Pattern splitPattern;

    /**
     * 合并相邻小片段时使用的连接符
     */
    private final String joinDelimiter;

    /**
     * 每个 segment 的最大字符数
     */
    private final int maxSegmentSize;

    /**
     * 相邻 segment 之间的重叠字符数
     */
    private final int overlapSize;

    /**
     * 完整构造
     *
     * @param regex          切分正则表达式
     * @param joinDelimiter  合并片段时的连接符（通常为 "\n\n"）
     * @param maxSegmentSize 单个 segment 最大字符数
     * @param overlapSize    相邻 segment 重叠字符数
     */
    public RegexTextSplitter(String regex, String joinDelimiter, int maxSegmentSize, int overlapSize) {
        if (regex == null || regex.isBlank())
            throw new IllegalArgumentException("正则表达式不能为空");
        if (maxSegmentSize <= 0)
            throw new IllegalArgumentException("maxSegmentSize 必须大于 0");
        if (overlapSize < 0)
            throw new IllegalArgumentException("overlapSize 不能为负数");
        if (overlapSize >= maxSegmentSize)
            throw new IllegalArgumentException("overlapSize 必须小于 maxSegmentSize");
        this.splitPattern = Pattern.compile(regex);
        this.joinDelimiter = joinDelimiter != null ? joinDelimiter : "\n\n";
        this.maxSegmentSize = maxSegmentSize;
        this.overlapSize = overlapSize;
    }

    /**
     * 基于固定分隔符创建切分器
     * <p>
     * 内部使用 {@code Pattern.quote} 对分隔符进行转义，避免被当作正则元字符处理。
     * </p>
     *
     * @param separator      固定分隔符字符串
     * @param maxSegmentSize 单个 segment 最大字符数
     * @param overlapSize    相邻 segment 重叠字符数
     * @return 切分器实例
     */
    public static RegexTextSplitter fromSeparator(String separator, int maxSegmentSize, int overlapSize) {
        return new RegexTextSplitter(Pattern.quote(separator), "\n\n", maxSegmentSize, overlapSize);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document document : documents)
            result.addAll(splitDocument(document));
        return result;
    }

    /**
     * 对单个文档执行切分
     */
    private List<Document> splitDocument(Document document) {
        String text = document.getText();
        if (text == null || text.isBlank())
            return List.of();

        Map<String, Object> originalMetadata = document.getMetadata();

        // 1. 按正则切成 parts
        String[] parts = splitPattern.split(text);
        List<String> nonEmptyParts = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty())
                nonEmptyParts.add(trimmed);
        }

        if (nonEmptyParts.isEmpty())
            return List.of();

        // 2. 合并相邻 parts 直到接近 maxSegmentSize
        List<String> mergedSegments = mergePartsIntoSegments(nonEmptyParts);

        // 3. 对超限 segment 进行二次切分
        List<String> finalSegments = new ArrayList<>();
        for (String segment : mergedSegments) {
            if (segment.length() <= maxSegmentSize)
                finalSegments.add(segment);
            else
                finalSegments.addAll(subSplit(segment));
        }

        // 4. 处理 overlap
        List<String> overlappedSegments = applyOverlap(finalSegments);

        // 5. 转换为 Document
        List<Document> result = new ArrayList<>();
        for (String segment : overlappedSegments) {
            Map<String, Object> metadata = new HashMap<>(originalMetadata);
            result.add(new Document(segment, metadata));
        }

        log.debug("正则切分完成：原始文本 {} 字符 → {} 个段落", text.length(), result.size());
        return result;
    }

    /**
     * 将 parts 合并为不超过 maxSegmentSize 的 segments
     */
    private List<String> mergePartsIntoSegments(List<String> parts) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            if (current.isEmpty()) {
                // 当前段为空，直接添加
                current.append(part);
            } else {
                // 计算合并后的长度（加上 joinDelimiter）
                int mergedLength = current.length() + joinDelimiter.length() + part.length();
                if (mergedLength <= maxSegmentSize) {
                    // 可以合并
                    current.append(joinDelimiter).append(part);
                } else {
                    // 超限，保存当前段并开始新段
                    segments.add(current.toString());
                    current = new StringBuilder(part);
                }
            }
        }

        // 别忘了最后一段
        if (!current.isEmpty())
            segments.add(current.toString());

        return segments;
    }

    /**
     * 对超过 maxSegmentSize 的文本进行二次切分（按字符边界，尝试在单词/行边界断开）
     * <p>
     * 注意：子片段之间不做 overlap 处理，由外层 {@link #applyOverlap} 统一添加。
     * </p>
     */
    private List<String> subSplit(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxSegmentSize, text.length());

            // 尝试在单词或行边界断开
            if (end < text.length()) {
                int breakPoint = findBreakPoint(text, start, end);
                if (breakPoint > start)
                    end = breakPoint;
            }

            result.add(text.substring(start, end).trim());
            start = end;
        }

        // 移除末尾可能的空片段
        result.removeIf(String::isBlank);
        return result;
    }

    /**
     * 在 [start, end) 范围内寻找合适的断开点
     * <p>
     * 优先级：换行符 &gt; 空格 &gt; 强制在 end 处断开
     * </p>
     */
    private int findBreakPoint(String text, int start, int end) {
        // 从 end 往前找最近的换行符
        for (int i = end - 1; i > start + (maxSegmentSize / 2); i--)
            if (text.charAt(i) == '\n')
                return i + 1;

        // 从 end 往前找最近的空格
        for (int i = end - 1; i > start + (maxSegmentSize / 2); i--)
            if (Character.isWhitespace(text.charAt(i)))
                return i + 1;

        // 找不到合适的断点，强制在 end 处断开
        return end;
    }

    /**
     * 为相邻 segment 添加重叠内容
     * <p>
     * 注意：合并后的 segment 长度可能超过 maxSegmentSize（= overlapSize + 1 + segmentSize），
     * 这与 LangChain4j DocumentByRegexSplitter 的行为一致，overlap 作为额外的上下文信息附加。
     * </p>
     */
    private List<String> applyOverlap(List<String> segments) {
        if (overlapSize == 0 || segments.size() <= 1)
            return segments;

        List<String> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            if (i == 0) {
                result.add(segments.get(i));
            } else {
                // 从上一个 segment 的末尾取 overlapSize 个字符作为前缀
                String prevSegment = segments.get(i - 1);
                int overlapStart = Math.max(0, prevSegment.length() - overlapSize);
                String overlapText = prevSegment.substring(overlapStart);
                result.add(overlapText + "\n" + segments.get(i));
            }
        }
        return result;
    }
}
