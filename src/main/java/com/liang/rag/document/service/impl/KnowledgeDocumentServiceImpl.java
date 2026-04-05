package com.liang.rag.document.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.rag.common.convention.exception.ClientException;
import com.liang.rag.common.convention.exception.ServiceException;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.common.enums.ChunkStatus;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.entity.KnowledgeChunk;
import com.liang.rag.document.mapper.KnowledgeDocumentMapper;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.document.service.KnowledgeChunkService;
import com.liang.rag.infra.lock.DistributeLock;
import com.liang.rag.mq.event.DocumentConvertEvent;
import com.liang.rag.mq.event.DocumentUploadEvent;
import com.liang.rag.mq.producer.DocumentProcessProducer;
import com.liang.rag.mq.transaction.DocumentTransactionContext;
import com.liang.rag.mq.transaction.DocumentTransactionContext.TransactionAction;
import com.liang.rag.rag.splitter.ParentMarkdownSplitter;
import com.liang.rag.storage.FileStorageStrategy;
import com.liang.rag.storage.ObjectNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识文档服务实现类
 * <p>
 * 承接从 Controller 下沉的文档上传、切分等核心业务逻辑
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> implements KnowledgeDocumentService {

    private final Tika tika = new Tika();

    private final FileStorageStrategy fileStorageStrategy;
    private final ObjectNameResolver objectNameResolver;
    private final KnowledgeChunkService knowledgeChunkService;
    private final DocumentProcessProducer documentProcessProducer;

    /**
     * 上传文档（使用 RocketMQ 事务消息保证 DB + MQ 一致性）
     * <p>
     * 处理流程：
     * <ol>
     *     <li>上传文件到 MinIO（幂等操作，在事务之外）</li>
     *     <li>使用雪花算法预生成 docId</li>
     *     <li>发送 half message → 本地事务（DB INSERT）→ COMMIT</li>
     * </ol>
     * </p>
     */
    @Override
    @DistributeLock(scene = "document-upload", keyExpression = "#uploadUser")
    public KnowledgeDocument uploadDocument(MultipartFile file, String uploadUser, String accessibleBy) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ClientException("文件名不能为空");
        }

        // 1. 上传至对象存储（幂等操作，在事务边界之外）
        String fileUrl = fileStorageStrategy.upload(file, fileName);

        // 2. 构建文档实体，使用雪花算法预生成 docId（事务消息要求在发送 half message 前就知道 ID）
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocId(IdWorker.getId());
        document.setDocTitle(fileName);
        document.setUploadUser(uploadUser);
        document.setDocUrl(fileUrl);
        document.setAccessibleBy(accessibleBy);
        document.setRetryCount(0);

        // 3. 根据文件类型走不同分支
        boolean isPdf = isPdfFile(fileName) || isPdfContent(file);

        if (isPdf) {
            // PDF 文件：状态为 UPLOADED，发送到上传 topic → 触发 MinerU 转换
            document.setStatus(DocumentStatus.UPLOADED);

            DocumentUploadEvent event = DocumentUploadEvent.builder()
                    .documentId(document.getDocId())
                    .uploadUser(uploadUser)
                    .build();

            DocumentTransactionContext txCtx = DocumentTransactionContext.builder()
                    .action(TransactionAction.INSERT)
                    .document(document)
                    .build();

            // 发送事务消息：half message → DB INSERT → COMMIT
            documentProcessProducer.sendTransactionalUploadEvent(event, txCtx);
        } else {
            // 非 PDF 文件：直接标记为已转换，发送到转换 topic → 触发切分与向量化
            document.setStatus(DocumentStatus.CONVERTED);
            document.setConvertedDocUrl(fileUrl);

            DocumentConvertEvent event = DocumentConvertEvent.builder()
                    .documentId(document.getDocId())
                    .build();

            DocumentTransactionContext txCtx = DocumentTransactionContext.builder()
                    .action(TransactionAction.INSERT)
                    .document(document)
                    .build();

            documentProcessProducer.sendTransactionalConvertEvent(event, txCtx);
        }

        return document;
    }

    /**
     * 对文档进行切分
     */
    @Override
    @Transactional
    @DistributeLock(scene = "document-split", keyExpression = "#documentId")
    public int splitDocument(Long documentId) {
        // 1. 查询文档最新状态
        KnowledgeDocument document = getById(documentId);
        if (document == null) {
            throw new ClientException("文档不存在, documentId: " + documentId);
        }

        if (document.getConvertedDocUrl() == null) {
            throw new ClientException("文档尚未完成转换");
        }

        // 已切分的文档直接返回片段数
        if (document.getStatus() == DocumentStatus.CHUNKED) {
            long count = knowledgeChunkService.count(new QueryWrapper<KnowledgeChunk>()
                    .eq("document_id", documentId)
                    .eq("skip_embedding", 0));
            return (int) count;
        }

        if (document.getStatus() != DocumentStatus.CONVERTED) {
            throw new ClientException("文档状态不为 CONVERTED，无法进行切分");
        }

        // 2. 从对象存储下载转换后的文件内容
        String objectName = objectNameResolver.resolve(document.getConvertedDocUrl());
        if (objectName == null) {
            throw new ServiceException("无法解析文档 URL");
        }

        String content;
        try (InputStream inputStream = fileStorageStrategy.download(objectName)) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServiceException("下载文档失败: " + e.getMessage());
        }

        // 3. 使用 Spring AI DocumentTransformer 进行切分
        ParentMarkdownSplitter transformer = new ParentMarkdownSplitter(1000, 100);
        Document inputDoc = new Document(content);
        List<Document> splitDocs = transformer.apply(List.of(inputDoc));

        // 4. 转换为 KnowledgeChunk 并保存
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < splitDocs.size(); i++) {
            Document doc = splitDocs.get(i);
            Map<String, Object> metadata = doc.getMetadata();

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setText(doc.getText());
            chunk.setChunkId((String) metadata.get("chunkId"));
            chunk.setMetadata(JSON.toJSONString(metadata));
            chunk.setDocumentId(documentId);
            chunk.setChunkOrder(i);
            chunk.setStatus(ChunkStatus.SKIP_EMBEDDING);

            // 检查是否需要跳过嵌入
            Object skipFlag = metadata.get("skipEmbedding");
            chunk.setSkipEmbedding(skipFlag instanceof Integer val && val == 1 ? 1 : 0);

            chunks.add(chunk);
        }

        // 5. 批量保存
        boolean batchSaved = knowledgeChunkService.saveBatch(chunks);
        if (!batchSaved) {
            throw new ServiceException("保存知识片段失败");
        }

        // 6. 更新文档状态为 CHUNKED
        document.setStatus(DocumentStatus.CHUNKED);
        this.updateById(document);

        return chunks.size();
    }

    @Override
    public List<KnowledgeDocument> listByStatus(String status) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);
        return this.list(wrapper);
    }

    // ==================== 私有方法 ====================

    /**
     * 通过文件后缀判断是否为 PDF
     */
    private boolean isPdfFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    /**
     * 通过 Apache Tika 检测文件内容类型判断是否为 PDF
     */
    private boolean isPdfContent(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String mimeType = tika.detect(is);
            return "application/pdf".equals(mimeType);
        } catch (IOException e) {
            log.warn("文件类型检测失败: {}", e.getMessage());
            return false;
        }
    }
}
