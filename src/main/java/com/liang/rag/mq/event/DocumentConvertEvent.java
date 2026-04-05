package com.liang.rag.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档转换完成事件
 * <p>
 * 仅传输 documentId，Consumer 从 DB 查询最新数据，
 * 避免序列化整个 Entity 导致的数据快照过时问题。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentConvertEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文档 ID
     */
    private Long documentId;
}
