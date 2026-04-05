package com.liang.rag.mq.consumer;

import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.parser.PDFProcessService;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.mq.constant.MqConstant;
import com.liang.rag.mq.event.DocumentUploadEvent;
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
 * 监听【文件上传完成】事件，负责从对象存储下载原始文档，并调用 Mineru 处理解析与上传图片
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
    private final PDFProcessService PDFProcessService;
    private final FileStorageStrategy fileStorageStrategy;
    private final ObjectNameResolver objectNameResolver;

    @Override
    public void onMessage(DocumentUploadEvent event) {
        Long documentId = event.getDocumentId();
        log.info("MQ 消费开始: 文档上传事件, documentId: {}", documentId);

        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);
        if (document == null) {
            log.error("MQ 消费失败: 找不到文档记录, documentId: {}", documentId);
            return;
        }

        if (document.getStatus() != DocumentStatus.UPLOADED) {
            log.warn("MQ 消费跳过: 文档状态不为 UPLOADED, 当前状态: {}, documentId: {}", document.getStatus(), documentId);
            return;
        }

        String docUrl = document.getDocUrl();
        String objectName = objectNameResolver.resolve(docUrl);

        if (objectName == null) {
            log.error("MQ 消费失败: 无法解析文档对象名称, docUrl: {}", docUrl);
            return;
        }

        // 从 MinIO 获取原始文件流以进行处理。这里流会在 PDFProcessService 内部被安全关闭。
        try {
            InputStream inputStream = fileStorageStrategy.download(objectName);
            // 通过 RocketMQ 线程池同步调用业务方法进行 PDF 解析，不用再使用 @Async
            PDFProcessService.processDocument(document, inputStream);
            log.info("MQ 消费成功: 文档转换流程处理完毕, documentId: {}", documentId);
        } catch (Exception e) {
            log.error("MQ 消费引发异常: 文档转换处理失败, documentId: {}", documentId, e);
            // RocketMQ 默认会抛出异常让系统进行重试（根据配置的死信策略），这里不吃掉异常
            throw new RuntimeException("文档转换失败", e);
        }
    }
}
