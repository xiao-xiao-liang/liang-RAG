package com.liang.rag.document.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.rag.common.convention.exception.ClientException;
import com.liang.rag.common.convention.exception.ServiceException;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.common.enums.ChunkStatus;
import com.liang.rag.common.enums.FileType;
import com.liang.rag.common.enums.KnowledgeBaseType;
import com.liang.rag.document.entity.DocumentSplitParam;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.entity.KnowledgeChunk;
import com.liang.rag.document.mapper.KnowledgeDocumentMapper;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.document.service.KnowledgeChunkService;
import com.liang.rag.document.splitter.DocumentSplitterFactory;
import com.liang.rag.document.splitter.ExcelSplitter;
import com.liang.rag.infra.lock.DistributeLock;
import com.liang.rag.mq.event.DocumentUploadEvent;
import com.liang.rag.mq.producer.DocumentProcessProducer;
import com.liang.rag.storage.FileStorageStrategy;
import com.liang.rag.storage.ObjectNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final FileStorageStrategy fileStorageStrategy;
    private final ObjectNameResolver objectNameResolver;
    private final KnowledgeChunkService knowledgeChunkService;
    private final DocumentProcessProducer documentProcessProducer;

    /**
     * 上传文档
     * <p>
     * 仅负责将文件上传到 MinIO 并保存文档记录到 DB（状态为 UPLOADED）。
     * 后续的转换、切分、向量化由用户通过 {@code /split} 接口手动触发。
     * </p>
     */
    @Override
    @DistributeLock(scene = "document-upload", keyExpression = "#uploadUser")
    public KnowledgeDocument uploadDocument(MultipartFile file, String uploadUser, String title,
                                            String description, String knowledgeBaseType,
                                            String tableName, String accessibleBy) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ClientException("文件名不能为空");
        }

        // 1. 上传至对象存储
        String fileUrl = fileStorageStrategy.upload(file, fileName);

        // 2. 构建文档实体并保存到 DB
        KnowledgeDocument document = KnowledgeDocument.builder()
                .docId(IdWorker.getId())
                .docTitle(title != null ? title : fileName)
                .uploadUser(uploadUser)
                .docUrl(fileUrl)
                .accessibleBy(accessibleBy)
                .description(description)
                .retryCount(0)
                .status(DocumentStatus.UPLOADED)
                .build();

        // 解析知识库类型
        KnowledgeBaseType kbType = KnowledgeBaseType.DOCUMENT_SEARCH;
        if (knowledgeBaseType != null && !knowledgeBaseType.isBlank()) {
            try {
                kbType = KnowledgeBaseType.valueOf(knowledgeBaseType);
            } catch (IllegalArgumentException e) {
                log.warn("无法识别的知识库类型: {}, 使用默认值 DOCUMENT_SEARCH", knowledgeBaseType);
            }
        }
        document.setKnowledgeBaseType(kbType);
        document.setTableName(tableName);

        this.save(document);
        log.info("文档上传完成, docId: {}, docTitle: {}", document.getDocId(), document.getDocTitle());

        return document;
    }

    /**
     * 对文档进行切分
     */
    @Override
    @Transactional
    @DistributeLock(scene = "document-split", keyExpression = "#documentId")
    public int splitDocument(Long documentId, DocumentSplitParam param) {
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

        List<Document> splitDocs;
        try (InputStream inputStream = fileStorageStrategy.download(objectName)) {
            // 根据文件类型选择不同的切分策略
            String docUrl = document.getConvertedDocUrl();
            String extension = docUrl.substring(docUrl.lastIndexOf(".") + 1);
            FileType fileType = FileType.fromExtension(extension);

            if (fileType == FileType.EXCEL || fileType == FileType.CSV) {
                // Excel/CSV 使用专用的 ExcelSplitter
                int chunkSize = (param != null && param.getChunkSize() != null) ? param.getChunkSize() : 1000;
                ExcelSplitter splitter = new ExcelSplitter(chunkSize, false);
                splitDocs = splitter.split(inputStream.readAllBytes());
            } else {
                // 其他文档使用 DocumentSplitterFactory 根据参数分派
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                DocumentTransformer transformer = DocumentSplitterFactory.getInstance(param);
                Document inputDoc = new Document(content);
                splitDocs = transformer.apply(List.of(inputDoc));
            }
        } catch (ClientException | ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("下载或切分文档失败: " + e.getMessage());
        }

        // 3. 转换为 KnowledgeChunk 并保存
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < splitDocs.size(); i++) {
            Document doc = splitDocs.get(i);
            Map<String, Object> metadata = doc.getMetadata();

            // 优先使用切分器生成的 chunkId，没有则使用 Document 自带的 id，兜底用 UUID
            String chunkId = (String) metadata.get("chunkId");
            if (chunkId == null || chunkId.isBlank()) {
                chunkId = !doc.getId().isBlank() ? doc.getId() : UUID.randomUUID().toString();
            }

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setText(doc.getText());
            chunk.setChunkId(chunkId);
            chunk.setMetadata(JSON.toJSONString(metadata));
            chunk.setDocumentId(documentId);
            chunk.setChunkOrder(i);
            chunk.setStatus(ChunkStatus.SKIP_EMBEDDING);

            // 检查是否需要跳过嵌入
            Object skipFlag = metadata.get("skipEmbedding");
            chunk.setSkipEmbedding(skipFlag instanceof Integer val && val == 1 ? 1 : 0);

            chunks.add(chunk);
        }

        // 4. 批量保存
        boolean batchSaved = knowledgeChunkService.saveBatch(chunks);
        if (!batchSaved) {
            throw new ServiceException("保存知识片段失败");
        }

        // 5. 更新文档状态为 CHUNKED
        document.setStatus(DocumentStatus.CHUNKED);
        this.updateById(document);

        return chunks.size();
    }

    @Override
    public void submitSplitTask(Long documentId, DocumentSplitParam param) {
        // 1. 校验文档状态
        KnowledgeDocument document = getById(documentId);
        if (document == null)
            throw new ClientException("文档不存在, documentId: " + documentId);

        // 防止重复提交
        DocumentStatus status = document.getStatus();
        if (status == DocumentStatus.CHUNKED || status == DocumentStatus.VECTOR_STORED)
            throw new ClientException("文档已完成处理，请勿重复提交");
        if (status == DocumentStatus.CONVERTING)
            throw new ClientException("文档正在转换中，请稍后再试");
        if (status != DocumentStatus.UPLOADED)
            throw new ClientException("文档状态异常，无法触发切分，当前状态: " + status);


        // 2. 发送 UPLOAD_TOPIC 消息，触发完整流水线（转换 → 切分 → 向量化）
        String splitParamJson = (param != null) ? JSON.toJSONString(param) : null;
        DocumentUploadEvent event = DocumentUploadEvent.builder()
                .documentId(documentId)
                .uploadUser(document.getUploadUser())
                .splitParamJson(splitParamJson)
                .build();
        documentProcessProducer.sendDocumentUploadEvent(event);
        log.info("切分任务已提交至 MQ, documentId: {}", documentId);
    }

    @Override
    public List<KnowledgeDocument> listByStatus(String status) {
        QueryWrapper<KnowledgeDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);
        return this.list(wrapper);
    }
}
