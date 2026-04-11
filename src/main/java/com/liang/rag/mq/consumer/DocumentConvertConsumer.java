package com.liang.rag.mq.consumer;

import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.common.enums.FileType;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.parser.FileProcessService;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.mq.constant.MqConstant;
import com.liang.rag.mq.event.DocumentConvertEvent;
import com.liang.rag.mq.event.DocumentUploadEvent;
import com.liang.rag.mq.producer.DocumentProcessProducer;
import com.liang.rag.parser.factory.FileProcessServiceFactory;
import com.liang.rag.storage.FileStorageStrategy;
import com.liang.rag.storage.ObjectNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 文档转换消费者
 * <p>
 * 监听 {@code DOCUMENT_UPLOAD_TOPIC} 事件，负责判断文件类型并执行相应处理：
 * <ul>
 *     <li>PDF/Word → 调用 MinerU 转换为 Markdown，完成后由 MinerU 服务发送 {@code CONVERT_TOPIC}</li>
 *     <li>MD/TXT 等不需要转换的文件 → 直接标记 CONVERTED 并发送 {@code CONVERT_TOPIC}</li>
 * </ul>
 * </p>
 * <p>
 * 触发方式：由用户通过 {@code POST /api/document/split/{documentId}} 接口发送 MQ 消息触发。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.DOCUMENT_UPLOAD_TOPIC,
        consumerGroup = MqConstant.CONSUMER_GROUP_CONVERT
)
public class DocumentConvertConsumer implements RocketMQListener<DocumentUploadEvent> {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final FileProcessServiceFactory fileProcessServiceFactory;
    private final FileStorageStrategy fileStorageStrategy;
    private final ObjectNameResolver objectNameResolver;
    private final DocumentProcessProducer documentProcessProducer;

    @Override
    public void onMessage(DocumentUploadEvent event) {
        Long documentId = event.getDocumentId();
        log.info("MQ 消费开始: 文档处理事件, documentId: {}", documentId);

        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);
        if (document == null) {
            log.error("MQ 消费失败: 找不到文档记录, documentId: {}", documentId);
            return;
        }

        if (document.getStatus() != DocumentStatus.UPLOADED) {
            log.warn("MQ 消费跳过: 文档状态不为 UPLOADED, 当前状态: {}, documentId: {}", document.getStatus(), documentId);
            return;
        }

        // 解析文件类型，判断是否需要 MinerU 转换（从 docUrl 解析，docTitle 可能不含后缀）
        String docUrl = document.getDocUrl();
        String extension = docUrl.substring(docUrl.lastIndexOf(".") + 1);
        FileType fileType = FileType.fromExtension(extension);
        FileProcessService fileProcessService = fileProcessServiceFactory.get(fileType, document.getKnowledgeBaseType());

        if (fileProcessService != null) {
            // 需要 MinerU 转换的文件（PDF/Word）
            processWithMinerU(document, fileProcessService, event.getSplitParamJson());
        } else {
            // 不需要转换的文件（MD/TXT 等），直接标记 CONVERTED 并触发切分
            processDirectly(document, event.getSplitParamJson());
        }
    }

    /**
     * 通过 MinerU 转换文件（PDF/Word → Markdown）
     * <p>
     * 转换完成后由 {@code MinerUProcessBaseServiceImpl} 负责发送 {@code CONVERT_TOPIC}。
     * splitParamJson 需要通过 MinerU 服务透传到 convert 事件中。
     * </p>
     */
    private void processWithMinerU(KnowledgeDocument document, FileProcessService fileProcessService, String splitParamJson) {
        String docUrl = document.getDocUrl();
        String objectName = objectNameResolver.resolve(docUrl);

        if (objectName == null) {
            log.error("MQ 消费失败: 无法解析文档对象名称, docUrl: {}", docUrl);
            return;
        }

        try {
            log.info("从源端提取预存对象 object: {}", objectName);
            InputStream inputStream = fileStorageStrategy.download(objectName);
            fileProcessService.processDocument(document, inputStream, splitParamJson);
            log.info("文档转换消费者处理结束, documentId: {}", document.getDocId());
        } catch (Exception e) {
            log.error("MQ 消费引发异常: 文档转换处理失败, documentId: {}", document.getDocId(), e);
            throw new RuntimeException("文档转换失败", e);
        }
    }

    /**
     * 直接处理不需要转换的文件（MD/TXT 等）
     * <p>
     * 将原始文件 URL 作为 convertedDocUrl，标记 CONVERTED 后发送 {@code CONVERT_TOPIC} 触发切分。
     * </p>
     */
    private void processDirectly(KnowledgeDocument document, String splitParamJson) {
        document.setStatus(DocumentStatus.CONVERTED);
        document.setConvertedDocUrl(document.getDocUrl());
        knowledgeDocumentService.updateById(document);
        log.info("文档无需转换，直接标记 CONVERTED, documentId: {}", document.getDocId());

        // 发送 CONVERT_TOPIC 触发切分 + 向量化
        DocumentConvertEvent convertEvent = DocumentConvertEvent.builder()
                .documentId(document.getDocId())
                .splitParamJson(splitParamJson)
                .build();
        documentProcessProducer.sendDocumentConvertEvent(convertEvent);
        log.info("已发送切分事件, documentId: {}", document.getDocId());
    }
}
