package com.liang.rag.document.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.rag.common.convention.result.Result;
import com.liang.rag.common.convention.result.Results;
import com.liang.rag.document.entity.DocumentSplitParam;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.parser.ImageDescriptionService;
import com.liang.rag.retrieval.service.VectorStoreService;
import com.liang.rag.storage.FileStorageStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文档管理控制器
 * <p>
 * 提供文档上传、查询、删除、切分等 API。
 * Controller 仅负责参数接收与结果返回，业务逻辑委托给 Service 层。
 * </p>
 */
@Validated
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final VectorStoreService vectorStoreService;
    private final ImageDescriptionService imageDescriptionService;
    private final FileStorageStrategy fileStorageStrategy;

    /**
     * 上传文档
     *
     * @param file             上传的文件
     * @param uploadUser       上传用户
     * @param title            文档标题
     * @param description      文档描述
     * @param knowledgeBaseType 知识库类型（DOCUMENT_SEARCH / DATA_QUERY）
     * @param tableName        表名（仅 DATA_QUERY 时使用，可为空）
     * @param accessibleBy     可见范围（可选）
     * @return 文档记录
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocument> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadUser") @NotBlank(message = "上传用户不能为空") String uploadUser,
            @RequestParam("title") @NotBlank(message = "文档标题不能为空") String title,
            @RequestParam("description") String description,
            @RequestParam("knowledgeBaseType") @NotBlank(message = "知识库类型不能为空") String knowledgeBaseType,
            @RequestParam(value = "tableName", required = false) String tableName,
            @RequestParam(value = "accessibleBy", required = false) String accessibleBy) {
        KnowledgeDocument document = knowledgeDocumentService.uploadDocument(
                file, uploadUser, title, description, knowledgeBaseType, tableName, accessibleBy);
        return Results.success(document);
    }

    /**
     * 对文档进行手动切分（异步）
     * <p>
     * 发送 MQ 消息至 {@code DOCUMENT_CONVERT_TOPIC}，由 {@code DocumentChunkConsumer} 异步消费执行切分与向量化。
     * </p>
     *
     * @param documentId 文档ID
     * @param param      切分参数
     * @return 提交结果
     */
    @PostMapping("/split/{documentId}")
    public Result<String> splitDocument(@PathVariable Long documentId, @RequestBody(required = false) @Valid DocumentSplitParam param) {
        knowledgeDocumentService.submitSplitTask(documentId, param);
        return Results.success("切分任务已提交，请稍后查询文档状态");
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
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") Integer current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于1") Integer size) {
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
    public Result<List<KnowledgeDocument>> listByStatus(
            @RequestParam @NotBlank(message = "状态参数不能为空") String status) {
        return Results.success(knowledgeDocumentService.listByStatus(status));
    }

    /**
     * 根据 ID 删除文档
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Results.success(knowledgeDocumentService.removeById(id));
    }

    /**
     * 【调试】测试图片描述生成
     * <p>
     * 上传一张图片到 MinIO，然后用其 URL 调用 qwen3-vl-plus 生成描述。
     * 仅用于调试 {@code ImageDescriptionService} 功能是否正常。
     * </p>
     *
     * @param file 图片文件
     * @return 图片描述文本
     */
    @PostMapping("/test/image-description")
    public Result<String> testImageDescription(@RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename();
        // 上传到 MinIO 拿到可访问的 URL
        String imageUrl = fileStorageStrategy.upload(file, "test/" + fileName);
        String description = imageDescriptionService.generateDescription(imageUrl, fileName);
        return Results.success(description);
    }
}

