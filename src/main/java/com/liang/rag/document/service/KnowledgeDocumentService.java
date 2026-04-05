package com.liang.rag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.rag.document.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识文档服务接口
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    /**
     * 上传文档
     * <p>
     * 1. 将文件上传至对象存储
     * 2. 保存文档记录到数据库
     * 3. 如为 PDF 文件，异步触发 MinerU 转换
     * 4. 如为非 PDF 文件（如 Markdown/TXT），直接标记为已转换
     * </p>
     *
     * @param file         上传的文件
     * @param uploadUser   上传用户
     * @param accessibleBy 可见范围（可选）
     * @return 文档记录
     * @throws IOException 文件读取异常
     */
    KnowledgeDocument uploadDocument(MultipartFile file, String uploadUser, String accessibleBy) throws IOException;

    /**
     * 对文档进行切分
     * <p>
     * 使用 MarkdownHeaderParentDocumentTransformer 基于 Markdown 标题层级切分文档，
     * 支持父子分段关系。切分结果保存到 knowledge_segment 表。
     * </p>
     *
     * @param documentId 文档 ID
     * @return 切分后的片段数量
     */
    int splitDocument(Long documentId);

    /**
     * 根据状态查询文档列表
     *
     * @param status 文档状态
     * @return 文档列表
     */
    List<KnowledgeDocument> listByStatus(String status);
}
