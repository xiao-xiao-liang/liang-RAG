package com.liang.rag.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.liang.rag.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表元数据实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("table_meta")
public class TableMeta extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表描述
     */
    private String description;

    /**
     * 建表的完整SQL语句
     */
    private String createSql;

    /**
     * 列元数据信息(JSON格式)
     */
    private String columnsInfo;
}
