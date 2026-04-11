package com.liang.rag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.rag.document.entity.DocumentSplitParam;
import com.liang.rag.document.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文档服务接口
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    /**
     * 上传文档
     * <p>
     * 仅负责将文件上传到 MinIO 并保存文档记录到 DB（状态为 UPLOADED）。
     * 后续的转换、切分、向量化由用户通过 {@code /split} 接口手动触发。
     * </p>
     *
     * @param file             上传的文件
     * @param uploadUser       上传用户
     * @param title            文档标题
     * @param description      文档描述
     * @param knowledgeBaseType 知识库类型
     * @param tableName        表名（仅 DATA_QUERY 时使用，可为空）
     * @param accessibleBy     可见范围（可选）
     * @return 文档记录
     */
    KnowledgeDocument uploadDocument(MultipartFile file, String uploadUser, String title,
                                     String description, String knowledgeBaseType,
                                     String tableName, String accessibleBy);

    /**
     * 对文档进行切分
     * <p>
     * 根据传入的切分参数，使用对应的 Splitter 对文档进行切分。
     * 支持标题、长度、正则、智能等多种切分策略。
     * 切分结果保存到 knowledge_segment 表。
     * </p>
     *
     * @param documentId 文档 ID
     * @param param      切分参数（可为空，为空时使用默认策略）
     * @return 切分后的片段数量
     */
    int splitDocument(Long documentId, DocumentSplitParam param);

    /**
     * 提交切分任务（异步）
     * <p>
     * 校验文档状态后，发送 MQ 消息到 {@code DOCUMENT_UPLOAD_TOPIC}，
     * 触发完整的文档处理流水线：转换（PDF/Word → MD）→ 切分 → 向量化。
     * </p>
     *
     * @param documentId 文档 ID
     * @param param      切分参数（可为空，为空时使用默认策略）
     */
    void submitSplitTask(Long documentId, DocumentSplitParam param);

    /**
     * 根据状态查询文档列表
     *
     * @param status 文档状态
     * @return 文档列表
     */
    List<KnowledgeDocument> listByStatus(String status);
}
