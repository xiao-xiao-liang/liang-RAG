package com.liang.rag.common.constant;

/**
 * 文档/片段元数据的键常量
 * <p>用于在 Spring AI Document 的 metadata 中统一管理键名</p>
 *
 * @author liang
 */
public final class MetadataKeyConstant {

    private MetadataKeyConstant() {
    }

    /** 文件名称 */
    public static final String FILE_NAME = "fileName";

    /** 文档ID */
    public static final String DOC_ID = "docId";

    /** 分片ID（每个片段的唯一标识） */
    public static final String CHUNK_ID = "chunkId";

    /** 父块ID（父子分段模式下，子片段指向父片段） */
    public static final String PARENT_CHUNK_ID = "parentChunkId";

    /** 同级块ID（兄弟分段模式下，同组片段共享此ID） */
    public static final String BROTHER_CHUNK_ID = "brotherChunkId";

    /** 兄弟块索引（同组中的序号，从1开始） */
    public static final String BROTHER_CHUNK_INDEX = "brotherChunkIndex";

    /** 兄弟块总数 */
    public static final String BROTHER_CHUNK_TOTAL = "brotherChunkTotal";

    /** 标题级别（1-6） */
    public static final String HEADER_LEVEL = "headerLevel";

    /** 访问权限 */
    public static final String ACCESSIBLE_BY = "accessibleBy";

    /** 文件地址 */
    public static final String URL = "url";

    /** 文件版本 */
    public static final String VERSION = "version";

    /** 分类 */
    public static final String CATEGORY = "category";

    /** 摘要 */
    public static final String SUMMARY = "summary";

    /** 关键字 */
    public static final String KEYWORDS = "keywords";

    /** 跳过 Embedding 标记（1=跳过，不生成向量） */
    public static final String SKIP_EMBEDDING = "skipEmbedding";
}
