package com.liang.rag.document.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.rag.common.convention.result.Result;
import com.liang.rag.common.convention.result.Results;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识文档管理控制器
 * <p>
 * 提供文档上传、查询、删除、切分等 API。
 * Controller 仅负责参数接收与结果返回，业务逻辑委托给 Service 层。
 * </p>
 */
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final VectorStoreService vectorStoreService;

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocument> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadUser") String uploadUser,
            @RequestParam(value = "accessibleBy", required = false) String accessibleBy) throws IOException {
        KnowledgeDocument document = knowledgeDocumentService.uploadDocument(file, uploadUser, accessibleBy);
        return Results.success(document);
    }

    /**
     * 对文档进行切分
     */
    @PostMapping("/split/{documentId}")
    public Result<Integer> splitDocument(@PathVariable Long documentId) {
        int count = knowledgeDocumentService.splitDocument(documentId);
        return Results.success(count);
    }

    /**
     * 向量化文档 (供测试使用)
     */
    @PostMapping("/embedding/{documentId}")
    public Result<Integer> embeddingDocument(@PathVariable Long documentId) {
        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);
        int count = vectorStoreService.embedAndStore(document);
        return Results.success(count);
    }

    /**
     * 分页查询文档列表
     */
    @GetMapping("/page")
    public Result<Page<KnowledgeDocument>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<KnowledgeDocument> page = knowledgeDocumentService.page(new Page<>(current, size));
        return Results.success(page);
    }

    /**
     * 根据 ID 查询文档详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeDocument> getById(@PathVariable Long id) {
        KnowledgeDocument document = knowledgeDocumentService.getById(id);
        return Results.success(document);
    }

    /**
     * 根据状态查询文档列表
     */
    @GetMapping("/list-by-status")
    public Result<List<KnowledgeDocument>> listByStatus(@RequestParam String status) {
        return Results.success(knowledgeDocumentService.listByStatus(status));
    }

    /**
     * 根据 ID 删除文档
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Results.success(knowledgeDocumentService.removeById(id));
    }


}
