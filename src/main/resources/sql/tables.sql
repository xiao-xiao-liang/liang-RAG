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
    `expire_date`       DATE          NULL     COMMENT '文档失效日期',
    `status`            VARCHAR(32)   NOT NULL COMMENT '状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
    `accessible_by`     VARCHAR(1024) NULL     COMMENT '可见范围',
    `description`       VARCHAR(512)  NULL     COMMENT '文档描述',
    `knowledge_base_type` VARCHAR(32) NULL COMMENT '知识库类型：DOCUMENT_SEARCH, DATA_QUERY',
    `table_name`        VARCHAR(128)  NULL     COMMENT '物理表名（仅限类型为DATA_QUERY时）',
    `extension`         TEXT          NULL     COMMENT '扩展字段，保存JSON字符串',
    `retry_count`       INT           NULL     DEFAULT 0 COMMENT '补偿重试次数（默认0，上限3）',
    `lock_version`      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `del_flag`          TINYINT       NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`doc_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_status_doc_id` (`status`, `doc_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '知识文档表';

-- 知识片段表
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
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
    `lock_version`   INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `del_flag`       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    -- 文档ID索引
    INDEX `idx_document_id` (`document_id`),
    -- 复合索引：文档ID+顺序，优化按文档查询并排序
    INDEX `idx_document_id_chunk_order` (`document_id`, `chunk_order`),
    -- 复合索引：文档ID+状态+跳过嵌入，优化向量化补偿任务查询
    INDEX `idx_document_status_skip` (`document_id`, `status`, `skip_embedding`),
    -- 状态索引，优化按状态查询
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '知识片段表';

-- 表元数据表
CREATE TABLE IF NOT EXISTS `table_meta` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `table_name`     VARCHAR(255)  NOT NULL COMMENT '表名',
    `description`    VARCHAR(512)  NULL     COMMENT '表描述',
    `create_sql`     TEXT          NOT NULL COMMENT '建表的完整SQL语句',
    `columns_info`   TEXT          NOT NULL COMMENT '列元数据信息(JSON格式)',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `lock_version`   INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `del_flag`        TINYINT       NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '用户自定义表元数据表';

-- AI会话表
CREATE TABLE `chat_conversation`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话唯一标识',
    `user_id`         VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `title`           VARCHAR(512) NULL COMMENT '会话标题',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT '状态',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `lock_version`    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_id` (`conversation_id`)
) ENGINE = InnoDB COMMENT = 'AI会话表';

-- 会话消息表
CREATE TABLE `chat_message`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`      VARCHAR(64)  NOT NULL COMMENT '消息唯一标识',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '所属会话ID',
    `type`            VARCHAR(32)  NOT NULL COMMENT '角色：USER/ASSISTANT',
    `content`         LONGTEXT     NOT NULL COMMENT '消息内容',
    `rewrite_content` LONGTEXT     NULL COMMENT '改写后的内容',
    `token_count`     INT          NULL COMMENT 'Token数量',
    `model_name`      VARCHAR(128) NULL COMMENT '使用的模型名称',
    `rag_references`  JSON         NULL COMMENT 'RAG引用内容JSON数组，包含document_id、document_title、chunk_id、chunk_content、similarity_score、retrieval_source等字段',
    `metadata`        JSON         NULL COMMENT '扩展元数据JSON格式',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `lock_version`    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB COMMENT = '会话消息表';