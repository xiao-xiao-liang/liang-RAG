package com.liang.rag.document.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.service.KnowledgeDocumentService;
import com.liang.rag.mq.event.DocumentConvertEvent;
import com.liang.rag.mq.event.DocumentUploadEvent;
import com.liang.rag.mq.producer.DocumentProcessProducer;
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
 *
 * <table>
 *     <tr><th>文档状态</th><th>超时阈值</th><th>补偿动作</th></tr>
 *     <tr><td>UPLOADED</td><td>30 分钟</td><td>重发上传事件</td></tr>
 *     <tr><td>CONVERTING</td><td>60 分钟</td><td>重置为 UPLOADED + 重发上传事件</td></tr>
 *     <tr><td>CONVERTED</td><td>30 分钟</td><td>重发转换完成事件</td></tr>
 *     <tr><td>CHUNKED</td><td>30 分钟</td><td>重发转换完成事件（触发向量化）</td></tr>
 * </table>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCompensationTask {

    /** 最大补偿次数 */
    private static final int MAX_RETRY_COUNT = 3;

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final DocumentProcessProducer documentProcessProducer;

    /**
     * 每 10 分钟执行一次补偿扫描
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    public void compensateStuckDocuments() {
        log.info("[补偿任务] 开始扫描卡滞文档...");

        int compensatedCount = 0;
        compensatedCount += compensateUploaded();
        compensatedCount += compensateConverting();
        compensatedCount += compensateConverted();
        compensatedCount += compensateChunked();

        if (compensatedCount > 0) {
            log.info("[补偿任务] 本轮共补偿 {} 篇文档", compensatedCount);
        } else {
            log.info("[补偿任务] 无需补偿");
        }
    }

    /**
     * 补偿 UPLOADED 状态超过 30 分钟的文档
     * <p>
     * 根据文件类型区分补偿路径：
     * <ul>
     *     <li>PDF → 重发 upload 事件，走 MinerU 转换</li>
     *     <li>MD → 直接标记 CONVERTED，发送 convert 事件跳过转换</li>
     *     <li>其他类型 → 暂不处理，标记 FAILED</li>
     * </ul>
     * </p>
     */
    private int compensateUploaded() {
        List<KnowledgeDocument> docs = findStuckDocuments(DocumentStatus.UPLOADED, 30);
        for (KnowledgeDocument doc : docs) {
            if (exceedMaxRetry(doc)) continue;

            try {
                String fileName = doc.getDocTitle();
                if (isPdf(fileName)) {
                    // PDF 文件：重发上传事件 → MinerU 转换
                    documentProcessProducer.sendDocumentUploadEvent(
                            DocumentUploadEvent.builder()
                                    .documentId(doc.getDocId())
                                    .uploadUser(doc.getUploadUser())
                                    .build()
                    );
                    incrementRetryCount(doc);
                    log.info("[补偿任务] PDF 文件重发上传事件, documentId: {}, retryCount: {}", doc.getDocId(), doc.getRetryCount());
                } else if (isMarkdown(fileName)) {
                    // MD 文件：无需转换，直接标记 CONVERTED 并发送 convert 事件
                    doc.setStatus(DocumentStatus.CONVERTED);
                    doc.setConvertedDocUrl(doc.getDocUrl());
                    knowledgeDocumentService.updateById(doc);

                    documentProcessProducer.sendDocumentConvertEvent(
                            DocumentConvertEvent.builder().documentId(doc.getDocId()).build()
                    );
                    incrementRetryCount(doc);
                    log.info("[补偿任务] MD 文件直接标记 CONVERTED 并重发转换事件, documentId: {}, retryCount: {}",
                            doc.getDocId(), doc.getRetryCount());
                } else {
                    // 其他类型暂不支持处理
                    log.warn("[补偿任务] 不支持的文件类型, 标记为 FAILED, documentId: {}, fileName: {}",
                            doc.getDocId(), fileName);
                    doc.setStatus(DocumentStatus.FAILED);
                    knowledgeDocumentService.updateById(doc);
                }
            } catch (Exception e) {
                log.error("[补偿任务] 补偿 UPLOADED 文档失败, documentId: {}", doc.getDocId(), e);
            }
        }
        return docs.size();
    }

    /**
     * 补偿 CONVERTING 状态超过 60 分钟的文档
     * <p>
     * CONVERTING 状态只有 PDF 文件才会进入（MinerU 转换中），
     * 因此统一重置为 UPLOADED 后按文件类型重新走补偿流程。
     * </p>
     */
    private int compensateConverting() {
        List<KnowledgeDocument> docs = findStuckDocuments(DocumentStatus.CONVERTING, 60);
        for (KnowledgeDocument doc : docs) {
            if (exceedMaxRetry(doc)) {
                continue;
            }
            try {
                // 重置为 UPLOADED 状态，下一轮 compensateUploaded 会根据文件类型分流
                doc.setStatus(DocumentStatus.UPLOADED);
                knowledgeDocumentService.updateById(doc);

                // PDF 直接重发上传事件
                documentProcessProducer.sendDocumentUploadEvent(
                        DocumentUploadEvent.builder()
                                .documentId(doc.getDocId())
                                .uploadUser(doc.getUploadUser())
                                .build()
                );
                incrementRetryCount(doc);
                log.info("[补偿任务] 重置并重发上传事件(CONVERTING→UPLOADED), documentId: {}, retryCount: {}",
                        doc.getDocId(), doc.getRetryCount());
            } catch (Exception e) {
                log.error("[补偿任务] 补偿 CONVERTING 文档失败, documentId: {}", doc.getDocId(), e);
            }
        }
        return docs.size();
    }

    /**
     * 补偿 CONVERTED 状态超过 30 分钟的文档
     * <p>可能原因：转换完成事件发送成功，但切分消费失败</p>
     */
    private int compensateConverted() {
        List<KnowledgeDocument> docs = findStuckDocuments(DocumentStatus.CONVERTED, 30);
        for (KnowledgeDocument doc : docs) {
            if (exceedMaxRetry(doc)) {
                continue;
            }
            try {
                documentProcessProducer.sendDocumentConvertEvent(
                        DocumentConvertEvent.builder().documentId(doc.getDocId()).build()
                );
                incrementRetryCount(doc);
                log.info("[补偿任务] 重发转换完成事件, documentId: {}, retryCount: {}",
                        doc.getDocId(), doc.getRetryCount());
            } catch (Exception e) {
                log.error("[补偿任务] 重发转换完成事件失败, documentId: {}", doc.getDocId(), e);
            }
        }
        return docs.size();
    }

    /**
     * 补偿 CHUNKED 状态超过 30 分钟的文档
     * <p>可能原因：切分成功但向量化失败</p>
     */
    private int compensateChunked() {
        List<KnowledgeDocument> docs = findStuckDocuments(DocumentStatus.CHUNKED, 30);
        for (KnowledgeDocument doc : docs) {
            if (exceedMaxRetry(doc)) {
                continue;
            }
            try {
                // 重发到 convert topic，让 ChunkConsumer 重新执行向量化
                documentProcessProducer.sendDocumentConvertEvent(
                        DocumentConvertEvent.builder().documentId(doc.getDocId()).build()
                );
                incrementRetryCount(doc);
                log.info("[补偿任务] 重发转换完成事件(触发向量化), documentId: {}, retryCount: {}",
                        doc.getDocId(), doc.getRetryCount());
            } catch (Exception e) {
                log.error("[补偿任务] 补偿 CHUNKED 文档失败, documentId: {}", doc.getDocId(), e);
            }
        }
        return docs.size();
    }

    // ==================== 工具方法 ====================

    /**
     * 查询指定状态下超过阈值时间的文档
     */
    private List<KnowledgeDocument> findStuckDocuments(DocumentStatus status, int minutesThreshold) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutesThreshold);
        return knowledgeDocumentService.list(
                new QueryWrapper<KnowledgeDocument>()
                        .eq("status", status)
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

    /**
     * 通过文件后缀判断是否为 PDF
     */
    private boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    /**
     * 通过文件后缀判断是否为 Markdown
     */
    private boolean isMarkdown(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }
}
