package com.liang.rag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.rag.document.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档表 Mapper 接口
 *
 * @author liang
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
