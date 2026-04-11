package com.liang.rag.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.document.entity.DocumentSplitParam;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.mq.constant.MqConstant;
import com.liang.rag.mq.event.DocumentConvertEvent;
import com.liang.rag.retrieval.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文档切分与向量化消费者
 * <p>
 * 监听 {@code DOCUMENT_CONVERT_TOPIC} 事件，负责对文档进行切分，
 * 并在切分成功后立刻执行向量化入库操作，打通 RAG 最后一步。
 * </p>
 * <p>
 * 触发方式：由用户通过 {@code POST /api/document/split/{documentId}} 接口手动发送 MQ 消息，
 * 不再在上传/转换完成后自动触发。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.DOCUMENT_CONVERT_TOPIC,
        consumerGroup = MqConstant.CONSUMER_GROUP_CHUNK
)
public class DocumentChunkConsumer implements RocketMQListener<DocumentConvertEvent> {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final VectorStoreService vectorStoreService;

    @Override
    public void onMessage(DocumentConvertEvent event) {
        Long documentId = event.getDocumentId();
        log.info("MQ 消费开始: 切分任务事件, 准备执行切分并向量化, documentId: {}", documentId);

        // 从 DB 查询最新文档数据，而非依赖 MQ 消息中的快照
        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);
        if (document == null) {
            log.error("MQ 消费失败: 找不到文档记录, documentId: {}", documentId);
            return;
        }

        if (document.getStatus() != DocumentStatus.CONVERTED) {
            log.warn("MQ 消费跳过: 文档状态不为 CONVERTED, 当前状态: {}, documentId: {}",
                    document.getStatus(), documentId);
            return;
        }

        try {
            // 1. 解析切分参数
            DocumentSplitParam param = null;
            if (event.getSplitParamJson() != null && !event.getSplitParamJson().isBlank()) {
                param = JSON.parseObject(event.getSplitParamJson(), DocumentSplitParam.class);
            }

            // 2. 切分文档
            log.info("开始执行文档切分, documentId: {}", documentId);
            int splitCount = knowledgeDocumentService.splitDocument(documentId, param);
            log.info("文档切分完成, 切出片段数: {}, documentId: {}", splitCount, documentId);

            // 3. 将切分好的片段存入向量数据库（需要重新查 DB 获取最新状态）
            KnowledgeDocument updatedDoc = knowledgeDocumentService.getById(documentId);
            log.info("开始执行片段入库 (Embedding -> Milvus), documentId: {}", documentId);
            int storedCount = vectorStoreService.embedAndStore(updatedDoc);
            log.info("文档向量化打通完成! 成功入库: {}, documentId: {}", storedCount, documentId);
        } catch (Exception e) {
            log.error("MQ 消费引发异常: 文档切分/向量化处理失败, documentId: {}", documentId, e);
            throw new RuntimeException("文档切分或向量化失败", e);
        }
    }
}
