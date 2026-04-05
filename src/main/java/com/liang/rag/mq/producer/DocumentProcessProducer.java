package com.liang.rag.mq.producer;

import com.liang.rag.common.convention.exception.ServiceException;
import com.liang.rag.mq.constant.MqConstant;
import com.liang.rag.mq.event.DocumentConvertEvent;
import com.liang.rag.mq.event.DocumentUploadEvent;
import com.liang.rag.mq.transaction.DocumentTransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 文档处理消息生产者
 * <p>
 * 提供两种发送模式：
 * <ul>
 *     <li>普通同步发送 — 适用于无需事务保证的场景</li>
 *     <li>事务消息发送 — 保证 DB 操作 + MQ 发送的原子性</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送文档上传完成事件（事务消息）
     * <p>
     * 事务流程：half message → 本地事务（DB INSERT）→ COMMIT/ROLLBACK
     * </p>
     *
     * @param event 上传事件
     * @param txCtx 事务上下文（包含操作类型和文档实体）
     */
    public void sendTransactionalUploadEvent(DocumentUploadEvent event, DocumentTransactionContext txCtx) {
        Message<DocumentUploadEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader("documentId", String.valueOf(event.getDocumentId()))
                .build();

        try {
            rocketMQTemplate.sendMessageInTransaction(
                    MqConstant.DOCUMENT_UPLOAD_TOPIC,
                    message,
                    txCtx
            );
            log.info("[事务消息] 文档上传事件发送完成, documentId: {}", event.getDocumentId());
        } catch (Exception e) {
            log.error("[事务消息] 文档上传事件发送失败, documentId: {}", event.getDocumentId(), e);
            throw new ServiceException("发送文档上传事件失败: " + e.getMessage());
        }
    }

    /**
     * 发送文档转换完成事件（事务消息）
     * <p>
     * 事务流程：half message → 本地事务（DB UPDATE）→ COMMIT/ROLLBACK
     * </p>
     *
     * @param event 转换完成事件
     * @param txCtx 事务上下文
     */
    public void sendTransactionalConvertEvent(DocumentConvertEvent event, DocumentTransactionContext txCtx) {
        Message<DocumentConvertEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader("documentId", String.valueOf(event.getDocumentId()))
                .build();

        try {
            rocketMQTemplate.sendMessageInTransaction(
                    MqConstant.DOCUMENT_CONVERT_TOPIC,
                    message,
                    txCtx
            );
            log.info("[事务消息] 文档转换事件发送完成, documentId: {}", event.getDocumentId());
        } catch (Exception e) {
            log.error("[事务消息] 文档转换事件发送失败, documentId: {}", event.getDocumentId(), e);
            throw new ServiceException("发送文档转换事件失败: " + e.getMessage());
        }
    }

    /**
     * 发送文档上传完成事件（普通同步发送，用于补偿任务重发）
     *
     * @param event 事件对象
     */
    public void sendDocumentUploadEvent(DocumentUploadEvent event) {
        SendResult sendResult = rocketMQTemplate.syncSend(
                MqConstant.DOCUMENT_UPLOAD_TOPIC,
                MessageBuilder.withPayload(event).build()
        );
        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            log.warn("文档上传事件发送失败, status: {}, documentId: {}", sendResult.getSendStatus(), event.getDocumentId());
            throw new ServiceException("发送文档上传事件失败, status: " + sendResult.getSendStatus());
        }
        log.info("文档上传事件发送成功, documentId: {}, msgId: {}", event.getDocumentId(), sendResult.getMsgId());
    }

    /**
     * 发送文档转换完成事件（普通同步发送，用于补偿任务重发）
     *
     * @param event 事件对象
     */
    public void sendDocumentConvertEvent(DocumentConvertEvent event) {
        SendResult sendResult = rocketMQTemplate.syncSend(
                MqConstant.DOCUMENT_CONVERT_TOPIC,
                MessageBuilder.withPayload(event).build()
        );
        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            log.warn("文档转换事件发送失败, status: {}, documentId: {}", sendResult.getSendStatus(), event.getDocumentId());
            throw new ServiceException("发送文档转换事件失败, status: " + sendResult.getSendStatus());
        }
        log.info("文档转换事件发送成功, documentId: {}, msgId: {}", event.getDocumentId(), sendResult.getMsgId());
    }
}
