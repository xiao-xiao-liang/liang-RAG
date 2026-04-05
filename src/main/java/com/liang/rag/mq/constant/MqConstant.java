package com.liang.rag.mq.constant;

/**
 * RocketMQ 相关常量
 */
public class MqConstant {
    
    /**
     * 文档上传完成事件主题
     * 用于通知消费者进行文档转换（PDF -> Markdown）
     */
    public static final String DOCUMENT_UPLOAD_TOPIC = "rag_document_upload_topic";
    
    /**
     * 文档转换完成事件主题
     * 用于通知消费者进行切分并写入向量数据库
     */
    public static final String DOCUMENT_CONVERT_TOPIC = "rag_document_convert_topic";
    
    /**
     * 消费者组名 — 文档转换
     */
    public static final String CONSUMER_GROUP_CONVERT = "rag_document_convert_group";
    
    /**
     * 消费者组名 — 文档切分
     */
    public static final String CONSUMER_GROUP_CHUNK = "rag_document_chunk_group";
}
