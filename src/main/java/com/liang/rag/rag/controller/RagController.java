package com.liang.rag.rag.controller;

import com.liang.rag.common.convention.result.Result;
import com.liang.rag.common.convention.result.Results;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索控制器
 * <p>提供文档向量化和向量相似度检索 API</p>
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final VectorStoreService vectorStoreService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    
    /**
     * 对指定文档进行向量化存储
     *
     * @param documentId 文档 ID
     * @return 成功向量化的片段数量
     */
    @PostMapping("/vectorize/{documentId}")
    public Result<Integer> vectorize(@PathVariable Long documentId) {
        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);
        int count = vectorStoreService.embedAndStore(document);
        return Results.success(count);
    }

    /**
     * 向量相似度检索
     *
     * @param query 查询文本
     * @param topK  返回的最大结果数（默认 5）
     * @return 相似文档列表
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        return Results.success(vectorStoreService.searchAsMapList(query, topK));
    }

    /**
     * 向量相似度检索（带阈值过滤）
     *
     * @param query              查询文本
     * @param topK               返回的最大结果数（默认 5）
     * @param similarityThreshold 相似度阈值（默认 0.7）
     * @return 相似文档列表
     */
    @GetMapping("/search-with-threshold")
    public Result<List<Map<String, Object>>> searchWithThreshold(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.7") double similarityThreshold) {
        return Results.success(vectorStoreService.searchAsMapList(query, topK, similarityThreshold));
    }
}
