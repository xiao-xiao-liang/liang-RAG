-- 建库
CREATE DATABASE IF NOT EXISTS `liang_rag` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `liang_rag`;

-- 知识文档表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `doc_id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `doc_title`         VARCHAR(1024) NOT NULL COMMENT '文档标题',
    `upload_user`       VARCHAR(255)  NULL     COMMENT '上传用户',
    `doc_url`           VARCHAR(2048) NULL     COMMENT '原始文档URL（存储在对象存储中）',
    `converted_doc_url` VARCHAR(2048) NULL     COMMENT '转换后的文档URL（Markdown/ZIP）',
    `status`            VARCHAR(32)   NOT NULL COMMENT '状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
    `accessible_by`     VARCHAR(1024) NULL     COMMENT '可见范围',
    `extension`         TEXT          NULL     COMMENT '扩展字段，保存JSON字符串',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`doc_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_status_doc_id` (`status`, `doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '知识文档表';

-- 知识片段表
CREATE TABLE IF NOT EXISTS `knowledge_segment` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '片段ID',
    `text`           LONGTEXT      NOT NULL COMMENT '文本内容',
    `chunk_id`       VARCHAR(255)  NULL     COMMENT '分片ID（UUID）',
    `metadata`       TEXT          NULL     COMMENT '元数据（JSON）',
    `document_id`    BIGINT        NOT NULL COMMENT '所属文档ID',
    `chunk_order`    INT           NOT NULL COMMENT '片段顺序',
    `embedding_id`   VARCHAR(255)  NULL     COMMENT '向量存储中的嵌入ID',
    `status`         VARCHAR(32)   NULL     COMMENT '状态：SKIP_EMBEDDING, VECTOR_STORED',
    `skip_embedding` INT           NULL     DEFAULT 0 COMMENT '是否跳过嵌入生成（1=跳过）',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_segment_document_id` (`document_id`),
    INDEX `idx_segment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '知识片段表';
