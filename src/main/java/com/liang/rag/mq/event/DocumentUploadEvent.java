package com.liang.rag.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档上传完成事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadEvent implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 文档 ID
     */
    private Long documentId;
    
    /**
     * 上传用户
     */
    private String uploadUser;

    /**
     * 切分参数 JSON（用户手动触发切分时传入，为空则使用默认策略）
     */
    private String splitParamJson;
}
