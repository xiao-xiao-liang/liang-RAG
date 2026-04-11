package com.liang.rag.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档转换完成事件 / 切分任务事件
 * <p>
 * 仅传输 documentId，Consumer 从 DB 查询最新数据，
 * 避免序列化整个 Entity 导致的数据快照过时问题。
 * </p>
 * <p>
 * 当用户手动触发切分时，{@code splitParamJson} 携带切分参数的 JSON 序列化字符串。
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

    /**
     * 切分参数 JSON（用户手动触发切分时传入，为空则使用默认策略）
     */
    private String splitParamJson;
}
