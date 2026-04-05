package com.liang.rag.common.enums;

/**
 * 文档处理状态枚举
 * <p>表示文档在整个处理管道中的生命周期状态</p>
 */
public enum DocumentStatus {

    /** 初始化 */
    INIT,

    /** 已上传至对象存储 */
    UPLOADED,

    /** 正在进行格式转换（如 PDF → Markdown） */
    CONVERTING,

    /** 格式转换完成 */
    CONVERTED,

    /** 已完成文档切分 */
    CHUNKED,

    /** 已存入向量数据库 */
    VECTOR_STORED,

    /** 处理失败（超过最大重试次数） */
    FAILED
}
