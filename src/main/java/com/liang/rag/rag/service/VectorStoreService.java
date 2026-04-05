package com.liang.rag.rag.service;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liang.rag.common.convention.exception.ClientException;
import com.liang.rag.common.convention.exception.ServiceException;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.common.enums.ChunkStatus;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.entity.KnowledgeChunk;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.document.service.KnowledgeChunkService;
import com.liang.rag.infra.lock.DistributeLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 向量存储服务
 * <p>
 * 负责将切分后的知识片段进行 Embedding 并存入 Milvus 向量数据库，
 * 以及基于向量相似度的知识检索功能。
 * </p>
 * <p>
 * 利用 Spring AI 自动装配的 {@link VectorStore}（由 Milvus Starter 提供）
 * 和 {@link org.springframework.ai.embedding.EmbeddingModel}（由 DashScope Starter 提供）
 * 完成向量化和检索。
 * </p>
 */
@Slf4j
@Service
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final Executor embeddingExecutor;

    public VectorStoreService(
            VectorStore vectorStore,
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeChunkService knowledgeChunkService,
            @Qualifier("embeddingExecutor") Executor embeddingExecutor) {
        this.vectorStore = vectorStore;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeChunkService = knowledgeChunkService;
        this.embeddingExecutor = embeddingExecutor;
    }

    /**
     * 将指定文档的所有片段向量化并存入 Milvus
     * <p>
     * 处理逻辑：
     * 1. 验证文档状态为 CHUNKED
     * 2. 查询所有未跳过 Embedding 且状态为 INIT 的片段
     * 3. 转换为 Spring AI Document 并批量写入 VectorStore
     * 4. 更新片段状态为 VECTOR_STORED，记录 embeddingId
     * 5. 更新文档状态为 VECTOR_STORED
     * </p>
     *
     * @param document 文档
     * @return 成功向量化的片段数量
     */
    @Transactional
    @DistributeLock(scene = "embedding", keyExpression = "#document.docId")
    public int embedAndStore(KnowledgeDocument document) {
        // 1. 验证文档状态
        Assert.notNull(document, () -> new ClientException("文档不存在"));
        Long documentId = document.getDocId();
        if (document.getStatus() == DocumentStatus.VECTOR_STORED) {
            log.info("文档已完成向量化, documentId: {}", documentId);
            return 0;
        }
        if (document.getStatus() != DocumentStatus.CHUNKED) {
            throw new ClientException("文档状态不为 CHUNKED，无法进行向量化");
        }

        // 2. 查询需要向量化的片段（skipEmbedding=0 且 status=SKIP_EMBEDDING）
        QueryWrapper<KnowledgeChunk> queryWrapper = new QueryWrapper<KnowledgeChunk>()
                .eq("document_id", documentId)
                .eq("skip_embedding", 0)
                .eq("status", ChunkStatus.SKIP_EMBEDDING)
                .orderByAsc("chunk_order");
        List<KnowledgeChunk> chunks = knowledgeChunkService.list(queryWrapper);

        if (chunks.isEmpty()) {
            log.info("没有需要向量化的片段, documentId: {}", documentId);
            document.setStatus(DocumentStatus.VECTOR_STORED);
            knowledgeDocumentService.updateById(document);
            return 0;
        }

        // 3. 转换为 Spring AI Document
        List<Document> aiDocuments = chunk2Document(documentId, chunks);

        // 4. 将文本分批处理解决 DashScope API 单次 25 条的限制，并提升并发效率
        log.info("开始并发向量化, documentId: {}, 待处理总片段数量: {}", documentId, aiDocuments.size());
        int batchSize = 8; // DashScope Embedding API 实际限制为 10 条，设定为 8 留有安全冗余
        List<List<Document>> batches = ListUtil.partition(aiDocuments, batchSize);

        // 使用 CompletableFuture 并发执行各批次的入库（内部自动调用 Embedding API + 写入 Milvus）
        // 使用自定义 embeddingExecutor 线程池，替代 ForkJoinPool.commonPool()，实现隔离和限流
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    try {
                        log.info("正在执行并发离散向量化入库，处理片段数: {}", batch.size());
                        vectorStore.add(batch);
                    } catch (Exception e) {
                        log.error("分批向量化入库失败, 批次大小: {}", batch.size(), e);
                        throw new ServiceException("并发向量化失败: " + e.getMessage());
                    }
                }, embeddingExecutor)).toList();

        // 阻塞主线程，等待所有并发任务全部完成（若其中某批次抛出异常也会在此 join 时一并抛出外围捕获）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("所有分批并发向量化入库完成, documentId: {}", documentId);

        // TODO 优化事务处理
        // 5. 更新片段状态
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            chunk.setStatus(ChunkStatus.VECTOR_STORED);
            // 由于分批写入后 Spring AI 依旧成功注入了 id 到原引用中
            chunk.setEmbeddingId(aiDocuments.get(i).getId());
        }
        knowledgeChunkService.updateBatchById(chunks);

        // 6. 更新文档状态
        document.setStatus(DocumentStatus.VECTOR_STORED);
        knowledgeDocumentService.updateById(document);

        return chunks.size();
    }

    private List<Document> chunk2Document(Long documentId, List<KnowledgeChunk> chunks) {
        List<Document> aiDocuments = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("segmentId", chunk.getId());
            metadata.put("documentId", documentId);
            metadata.put("chunkId", chunk.getChunkId());
            metadata.put("chunkOrder", chunk.getChunkOrder());

            Document aiDoc = new Document(
                    chunk.getChunkId(),  // 使用 chunkId 作为 Document ID
                    chunk.getText(),
                    metadata
            );
            aiDocuments.add(aiDoc);
        }
        return aiDocuments;
    }

    /**
     * 基于向量相似度检索知识片段
     *
     * @param query 查询文本
     * @param topK  返回最相似的 K 个结果
     * @return 相似文档列表（含文本、元数据、相似度分数）
     */
    public List<Document> similaritySearch(String query, int topK) {
        log.info("向量检索, query: {}, topK: {}", query, topK);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.info("检索到 {} 条结果", results.size());
        return results;
    }

    /**
     * 基于向量相似度检索（带相似度阈值过滤）
     *
     * @param query               查询文本
     * @param topK                返回最相似的 K 个结果
     * @param similarityThreshold 相似度阈值（0-1），低于此阈值的结果将被过滤
     * @return 相似文档列表
     */
    public List<Document> similaritySearch(String query, int topK, double similarityThreshold) {
        log.info("向量检索, query: {}, topK: {}, threshold: {}", query, topK, similarityThreshold);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.info("检索到 {} 条结果（阈值过滤后）", results.size());
        return results;
    }

    /**
     * 基于向量相似度检索并转换为可序列化的 Map 列表
     *
     * @param query 查询文本
     * @param topK  返回最相似的 K 个结果
     * @return 可序列化的检索结果列表
     */
    public List<Map<String, Object>> searchAsMapList(String query, int topK) {
        List<Document> results = similaritySearch(query, topK);
        return convertToMapList(results);
    }

    /**
     * 基于向量相似度检索（带阈值）并转换为可序列化的 Map 列表
     *
     * @param query               查询文本
     * @param topK                返回最相似的 K 个结果
     * @param similarityThreshold 相似度阈值
     * @return 可序列化的检索结果列表
     */
    public List<Map<String, Object>> searchAsMapList(String query, int topK, double similarityThreshold) {
        List<Document> results = similaritySearch(query, topK, similarityThreshold);
        return convertToMapList(results);
    }

    /**
     * 将 Spring AI Document 列表转换为可序列化的 Map 列表
     */
    private List<Map<String, Object>> convertToMapList(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", doc.getId());
                    item.put("text", doc.getText());
                    item.put("metadata", doc.getMetadata());
                    return item;
                })
                .toList();
    }
}
