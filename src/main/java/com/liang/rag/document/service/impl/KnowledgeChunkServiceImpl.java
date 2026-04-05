package com.liang.rag.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.rag.document.entity.KnowledgeChunk;
import com.liang.rag.document.mapper.KnowledgeSegmentMapper;
import com.liang.rag.document.service.KnowledgeChunkService;
import org.springframework.stereotype.Service;

/**
 * 知识片段服务实现类
 *
 * @author liang
 */
@Service
public class KnowledgeChunkServiceImpl extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeChunk> implements KnowledgeChunkService {
}
