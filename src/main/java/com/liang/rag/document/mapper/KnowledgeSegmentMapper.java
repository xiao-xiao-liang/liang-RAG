package com.liang.rag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.rag.document.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识片段表 Mapper 接口
 *
 * @author liang
 */
@Mapper
public interface KnowledgeSegmentMapper extends BaseMapper<KnowledgeChunk> {
}
