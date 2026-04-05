package com.liang.rag.mq.transaction;

import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.mq.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 事务消息监听器
 * <p>
 * 核心职责：保证"DB 操作 + MQ 消息发送"的原子性。
 * </p>
 * <p>
 * 工作流程：
 * <ol>
 *     <li>Producer 发送 half message（对 Consumer 不可见）</li>
 *     <li>RocketMQ 回调 {@link #executeLocalTransaction} 执行本地事务（DB 操作）</li>
 *     <li>本地事务成功 → COMMIT → Consumer 可消费；失败 → ROLLBACK → 消息丢弃</li>
 *     <li>应用宕机 → RocketMQ 回查 {@link #checkLocalTransaction} → 根据 DB 状态决定</li>
 * </ol>
 * </p>
 *
 * @see DocumentTransactionContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQTransactionListener
public class DocumentTransactionListener implements RocketMQLocalTransactionListener {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 执行本地事务
     * <p>
     * 当 half message 发送成功后，RocketMQ 同步回调此方法。
     * 方法内执行 DB 操作（INSERT 或 UPDATE），根据执行结果返回 COMMIT 或 ROLLBACK。
     * </p>
     *
     * @param msg 消息体（包含 documentId 等 header 信息）
     * @param arg 事务上下文 {@link DocumentTransactionContext}
     * @return 事务状态：COMMIT / ROLLBACK / UNKNOWN
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        if (!(arg instanceof DocumentTransactionContext ctx)) {
            log.error("[事务消息] arg 类型不匹配, 期望 DocumentTransactionContext, 实际: {}",
                    arg != null ? arg.getClass().getName() : "null");
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        KnowledgeDocument document = ctx.getDocument();
        Long docId = document.getDocId();

        try {
            switch (ctx.getAction()) {
                case INSERT -> {
                    knowledgeDocumentService.save(document);
                    log.info("[事务消息] 本地事务执行成功(INSERT), docId: {}", docId);
                }
                case UPDATE -> {
                    knowledgeDocumentService.updateById(document);
                    log.info("[事务消息] 本地事务执行成功(UPDATE), docId: {}", docId);
                }
            }
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("[事务消息] 本地事务执行失败, docId: {}, action: {}", docId, ctx.getAction(), e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 事务回查
     * <p>
     * 当 Producer 发送 half message 后宕机或网络异常，RocketMQ 会定期回查此方法，
     * 以确认本地事务的最终状态。
     * </p>
     * <p>
     * 回查逻辑：
     * <ul>
     *     <li>根据 documentId 查询 DB</li>
     *     <li>文档不存在 → ROLLBACK（INSERT 未完成）</li>
     *     <li>上传事件：文档状态 >= UPLOADED → COMMIT</li>
     *     <li>转换事件：文档状态 >= CONVERTED → COMMIT</li>
     * </ul>
     * </p>
     *
     * @param msg 消息体
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String documentIdStr = (String) msg.getHeaders().get("documentId");
        if (documentIdStr == null) {
            log.warn("[事务回查] 消息缺少 documentId header, 执行 ROLLBACK");
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        Long documentId = Long.parseLong(documentIdStr);
        KnowledgeDocument document = knowledgeDocumentService.getById(documentId);

        if (document == null) {
            log.warn("[事务回查] 文档不存在, documentId: {}, 执行 ROLLBACK", documentId);
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        // 根据消息目标 topic 判断预期状态
        String destination = (String) msg.getHeaders().get("rocketmq_DESTINATION");
        log.info("[事务回查] documentId: {}, 当前状态: {}, 目标 topic: {}",
                documentId, document.getStatus(), destination);

        if (destination != null && destination.contains(MqConstant.DOCUMENT_UPLOAD_TOPIC)) {
            // 上传事件：文档存在且状态不是 INIT 即可认为事务已提交
            return document.getStatus() != DocumentStatus.INIT
                    ? RocketMQLocalTransactionState.COMMIT
                    : RocketMQLocalTransactionState.UNKNOWN;
        }

        if (destination != null && destination.contains(MqConstant.DOCUMENT_CONVERT_TOPIC)) {
            // 转换事件：文档状态已到 CONVERTED 或更后面的状态
            return document.getStatus().ordinal() >= DocumentStatus.CONVERTED.ordinal()
                    ? RocketMQLocalTransactionState.COMMIT
                    : RocketMQLocalTransactionState.UNKNOWN;
        }

        // 未知 topic，保守返回 UNKNOWN 等待下次回查
        return RocketMQLocalTransactionState.UNKNOWN;
    }
}
