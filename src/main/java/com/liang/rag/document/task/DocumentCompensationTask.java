package com.liang.rag.document.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文档处理补偿定时任务
 * <p>
 * 定期扫描卡在中间状态的文档，尝试重新投递 MQ 消息触发处理。
 * 超过最大补偿次数（3次）后标记为 FAILED，避免无限重试。
 * </p>
 * <p>
 * 由于上传和切分均由用户手动触发，仅补偿 CONVERTING 状态（MinerU 转换卡住）。
 * UPLOADED/CONVERTED/CHUNKED 属于用户手动操作前的正常等待状态，不自动补偿。
 * </p>
 *
 * <table>
 *     <tr><th>文档状态</th><th>超时阈值</th><th>补偿动作</th></tr>
 *     <tr><td>CONVERTING</td><td>60 分钟</td><td>重置为 UPLOADED</td></tr>
 * </table>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCompensationTask {

    /** 最大补偿次数 */
    private static final int MAX_RETRY_COUNT = 3;

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 每 10 分钟执行一次补偿扫描
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    public void compensateStuckDocuments() {
        log.info("[补偿任务] 开始扫描卡滞文档...");

        int compensatedCount = compensateConverting();

        if (compensatedCount > 0) {
            log.info("[补偿任务] 本轮共补偿 {} 篇文档", compensatedCount);
        } else {
            log.info("[补偿任务] 无需补偿");
        }
    }

    /**
     * 补偿 CONVERTING 状态超过 60 分钟的文档
     * <p>
     * CONVERTING 状态表示 MinerU 转换卡住，重置为 UPLOADED 让用户可以重新触发。
     * 不自动重发消息，因为原始的切分参数无法恢复。
     * </p>
     */
    private int compensateConverting() {
        List<KnowledgeDocument> docs = findStuckDocuments();
        for (KnowledgeDocument doc : docs) {
            if (exceedMaxRetry(doc)) {
                continue;
            }
            try {
                // 重置为 UPLOADED 状态，等待用户重新触发
                doc.setStatus(DocumentStatus.UPLOADED);
                knowledgeDocumentService.updateById(doc);
                incrementRetryCount(doc);
                log.info("[补偿任务] 重置文档状态(CONVERTING→UPLOADED), documentId: {}, retryCount: {}",
                        doc.getDocId(), doc.getRetryCount());
            } catch (Exception e) {
                log.error("[补偿任务] 补偿 CONVERTING 文档失败, documentId: {}", doc.getDocId(), e);
            }
        }
        return docs.size();
    }



    // ==================== 工具方法 ====================

    /**
     * 查询指定状态下超过阈值时间的文档
     */
    private List<KnowledgeDocument> findStuckDocuments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(60);
        return knowledgeDocumentService.list(
                new QueryWrapper<KnowledgeDocument>()
                        .eq("status", DocumentStatus.CONVERTING)
                        .lt("update_time", threshold)
                        .lt("IFNULL(retry_count, 0)", MAX_RETRY_COUNT)
        );
    }

    /**
     * 判断是否超过最大重试次数，超过则标记为 FAILED
     */
    private boolean exceedMaxRetry(KnowledgeDocument doc) {
        int currentRetry = Optional.ofNullable(doc.getRetryCount()).orElse(0);
        if (currentRetry >= MAX_RETRY_COUNT) {
            doc.setStatus(DocumentStatus.FAILED);
            knowledgeDocumentService.updateById(doc);
            log.warn("[补偿任务] 文档超过最大重试次数, 标记为 FAILED, documentId: {}, retryCount: {}", doc.getDocId(), currentRetry);
            return true;
        }
        return false;
    }

    /**
     * 递增文档的重试计数
     */
    private void incrementRetryCount(KnowledgeDocument doc) {
        int currentRetry = Optional.ofNullable(doc.getRetryCount()).orElse(0);
        doc.setRetryCount(currentRetry + 1);
        knowledgeDocumentService.updateById(doc);
    }

}
