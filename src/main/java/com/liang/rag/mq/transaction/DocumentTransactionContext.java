package com.liang.rag.mq.transaction;

import com.liang.rag.document.entity.KnowledgeDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档事务消息上下文
 * <p>
 * 作为 {@code sendMessageInTransaction} 的 arg 参数传递给
 * {@link DocumentTransactionListener#executeLocalTransaction}，
 * 封装本地事务所需的操作类型和文档数据。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTransactionContext {

    /**
     * 本地事务操作类型
     */
    private TransactionAction action;

    /**
     * 文档实体（需要 INSERT 或 UPDATE 的数据）
     */
    private KnowledgeDocument document;

    /**
     * 事务操作类型枚举
     */
    public enum TransactionAction {
        /** 新增文档记录 */
        INSERT,
        /** 更新文档记录 */
        UPDATE
    }
}
