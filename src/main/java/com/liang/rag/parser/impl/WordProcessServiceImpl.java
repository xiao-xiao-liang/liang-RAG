package com.liang.rag.parser.impl;

import com.liang.rag.common.enums.FileType;
import com.liang.rag.common.enums.KnowledgeBaseType;
import com.liang.rag.document.mapper.KnowledgeDocumentMapper;
import com.liang.rag.mq.producer.DocumentProcessProducer;
import com.liang.rag.parser.ImageDescriptionService;
import com.liang.rag.parser.config.DocumentProcessProperties;
import com.liang.rag.parser.config.MineruProperties;
import com.liang.rag.storage.FileStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Word文档处理服务
 * 负责调用 MinerU 解析 Word
 */
@Slf4j
@Service
public class WordProcessServiceImpl extends MinerUProcessBaseServiceImpl {

    public WordProcessServiceImpl(FileStorageStrategy fileStorageStrategy, KnowledgeDocumentMapper knowledgeDocumentMapper, MineruProperties mineruProperties, DocumentProcessProperties documentProcessProperties, ImageDescriptionService imageDescriptionService, DocumentProcessProducer documentProcessProducer) {
        super(fileStorageStrategy, knowledgeDocumentMapper, mineruProperties, documentProcessProperties, imageDescriptionService, documentProcessProducer);
    }

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileType == FileType.DOC;
    }
}
