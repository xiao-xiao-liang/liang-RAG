package com.liang.rag.document.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liang.rag.common.convention.result.Result;
import com.liang.rag.common.convention.result.Results;
import com.liang.rag.document.entity.KnowledgeChunk;
import com.liang.rag.document.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识片段管理控制器
 *
 * @author liang
 */
@RestController
@RequestMapping("/api/segment")
@RequiredArgsConstructor
public class KnowledgeSegmentController {

    private final KnowledgeChunkService knowledgeChunkService;

    /**
     * 分页查询片段列表
     */
    @GetMapping("/page")
    public Result<Page<KnowledgeChunk>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return Results.success(knowledgeChunkService.page(new Page<>(current, size)));
    }

    /**
     * 根据 ID 查询片段详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeChunk> getById(@PathVariable Long id) {
        KnowledgeChunk segment = knowledgeChunkService.getById(id);
        return Results.success(segment);
    }

    /**
     * 根据文档 ID 查询片段列表（按顺序排列）
     */
    @GetMapping("/list-by-document")
    public Result<List<KnowledgeChunk>> listByDocumentId(@RequestParam Long documentId) {
        QueryWrapper<KnowledgeChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId).orderByAsc("chunk_order");
        return Results.success(knowledgeChunkService.list(wrapper));
    }

    /**
     * 根据状态查询片段列表
     */
    @GetMapping("/list-by-status")
    public Result<List<KnowledgeChunk>> listByStatus(@RequestParam String status) {
        QueryWrapper<KnowledgeChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);
        return Results.success(knowledgeChunkService.list(wrapper));
    }

    /**
     * 新增片段
     */
    @PostMapping
    public Result<Boolean> save(@RequestBody KnowledgeChunk segment) {
        return Results.success(knowledgeChunkService.save(segment));
    }

    /**
     * 批量新增片段
     */
    @PostMapping("/batch")
    public Result<Boolean> saveBatch(@RequestBody List<KnowledgeChunk> segments) {
        return Results.success(knowledgeChunkService.saveBatch(segments));
    }

    /**
     * 更新片段
     */
    @PutMapping
    public Result<Boolean> updateById(@RequestBody KnowledgeChunk segment) {
        return Results.success(knowledgeChunkService.updateById(segment));
    }

    /**
     * 根据 ID 删除片段
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> removeById(@PathVariable Long id) {
        return Results.success(knowledgeChunkService.removeById(id));
    }

    /**
     * 批量删除片段
     */
    @DeleteMapping("/batch")
    public Result<Boolean> removeByIds(@RequestParam List<Long> ids) {
        return Results.success(knowledgeChunkService.removeByIds(ids));
    }
}
