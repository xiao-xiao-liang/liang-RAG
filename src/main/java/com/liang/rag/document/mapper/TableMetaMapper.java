package com.liang.rag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.rag.document.entity.TableMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 表元数据 Mapper
 * <p>
 * <b>安全警告</b>：{@link #executeCreateTable} 和 {@link #dropTable} 使用 MyBatis {@code ${}} 占位符（字符串替换），
 * 因为 DDL 语句中的表名无法使用 {@code #{}} 参数化绑定。
 * <b>调用方必须确保传入的参数已通过严格校验</b>（如前缀白名单、正则校验），否则存在 SQL 注入风险。
 * </p>
 */
@Mapper
public interface TableMetaMapper extends BaseMapper<TableMeta> {

    /**
     * 检查表是否存在
     *
     * @param tableName 表名
     * @return 存在返回 > 0
     */
    @Select("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = #{tableName}")
    int checkTableExists(@Param("tableName") String tableName);

    /**
     * 执行建表SQL
     * <p>
     * <b>安全注意</b>：此方法使用 ${} 直接拼接 SQL，调用方必须确保 SQL 来源可信（由程序生成且已校验）。
     * </p>
     *
     * @param createTableSql 建表 SQL 语句（由 ExcelProcessServiceImpl 程序生成）
     */
    @Update("<script>${createTableSql}</script>")
    void executeCreateTable(@Param("createTableSql") String createTableSql);

    /**
     * 删除物理表
     * <p>
     * <b>安全注意</b>：此方法使用 ${} 直接拼接表名，调用方必须确保 tableName 已通过前缀白名单 + 正则校验。
     * </p>
     *
     * @param tableName 表名（必须以 {@code custom_data_query_} 前缀开头）
     */
    @Update("DROP TABLE IF EXISTS `${tableName}`")
    void dropTable(@Param("tableName") String tableName);
}
