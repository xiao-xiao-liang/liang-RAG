package com.liang.rag.parser.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.rag.common.enums.FileType;
import com.liang.rag.common.enums.KnowledgeBaseType;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.entity.TableMeta;
import com.liang.rag.document.mapper.TableMetaMapper;
import com.liang.rag.parser.FileProcessService;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Excel处理服务实现类
 * <p>
 * 将 Excel/CSV 文件解析后动态建表并导入数据，用于"数据查询"型知识库。
 * </p>
 * <p>
 * 处理流程：
 * <ol>
 *     <li>解析 Excel 文件（I/O 操作，在事务外执行）</li>
 *     <li>执行 DDL 建表（DDL 会隐式提交事务，独立执行）</li>
 *     <li>在编程式事务中批量插入数据 + 保存元数据</li>
 *     <li>如步骤3失败，执行补偿删除物理表</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelProcessServiceImpl implements FileProcessService {

    private final JdbcTemplate jdbcTemplate;
    private final TableMetaMapper tableMetaMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 表名前缀
     */
    private static final String TABLE_PREFIX = "custom_data_query_";
    /**
     * 有效的表名正则表达式
     */
    private static final Pattern VALID_TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    @Override
    public void processDocument(KnowledgeDocument document, InputStream inputStream, String splitParamJson) {
        String documentTitle = document.getDocTitle();
        String originalTableName = document.getTableName();
        log.info("开始处理Excel文件: {}", documentTitle);

        // ========== 阶段1：解析 Excel 文件（I/O 操作，不在事务内） ==========
        List<List<String>> excelData;
        try {
            excelData = parseExcel(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        } finally {
            closeQuietly(inputStream);
        }

        if (excelData.isEmpty() || excelData.size() < 2)
            throw new IllegalArgumentException("Excel文件为空或只有表头，没有数据行");

        // 获取表头
        List<String> headers = excelData.getFirst();
        if (headers.isEmpty())
            throw new IllegalArgumentException("Excel表头为空");

        // 生成或验证表名
        String tableName = generateTableName(originalTableName);

        // 检查表名是否已存在
        if (tableMetaMapper.checkTableExists(tableName) > 0) {
            if (document.isOverride())
                dropTable(tableName);
            else
                throw new IllegalArgumentException("表 " + tableName + " 已存在");
        }

        // 生成列信息
        List<ColumnInfo> columns = generateColumnInfo(headers);

        // 生成建表SQL
        String createTableSql = generateCreateTableSql(tableName, document.getDescription(), columns);
        log.info("生成建表SQL: {}", createTableSql);

        // ========== 阶段2：执行 DDL 建表（DDL 会隐式提交，独立执行） ==========
        tableMetaMapper.executeCreateTable(createTableSql);
        log.info("表 {} 创建成功", tableName);

        // ========== 阶段3：在编程式事务中插入数据 + 保存元数据 ==========
        List<List<String>> dataRows = excelData.subList(1, excelData.size());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int insertedCount = insertData(tableName, columns, dataRows);
                log.info("插入数据 {} 行", insertedCount);

                // 保存表元数据
                TableMeta tableMeta = new TableMeta();
                tableMeta.setTableName(tableName);
                tableMeta.setDescription(document.getDescription() != null
                        ? document.getDescription()
                        : "从Excel导入: " + documentTitle);
                tableMeta.setCreateSql(createTableSql);
                tableMeta.setColumnsInfo(JSON.toJSONString(columns));
                tableMeta.setCreateTime(LocalDateTime.now());
                tableMeta.setUpdateTime(LocalDateTime.now());
                int result = tableMetaMapper.insert(tableMeta);
                Assert.isTrue(result == 1, "表元数据保存失败");
                log.info("表元数据保存成功, ID: {}", tableMeta.getId());
            });
        } catch (Exception e) {
            // DML 失败，补偿删除已创建的物理表
            log.error("数据插入或元数据保存失败，执行补偿删除物理表: {}", tableName, e);
            try {
                tableMetaMapper.dropTable(tableName);
                log.info("补偿删除物理表成功: {}", tableName);
            } catch (Exception dropEx) {
                log.error("补偿删除物理表失败: {}", tableName, dropEx);
            }
            throw new RuntimeException("Excel数据导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除物理表及其元数据
     *
     * @param tableName 表名
     */
    public void dropTable(String tableName) {
        // 安全检查：表名必须符合命名规范且以指定前缀开头
        if (!isValidTableName(tableName))
            throw new IllegalArgumentException("无效的表名: " + tableName);
        if (!tableName.startsWith(TABLE_PREFIX))
            throw new IllegalArgumentException("只允许删除前缀为 " + TABLE_PREFIX + " 的表: " + tableName);

        transactionTemplate.executeWithoutResult(status -> {
            // 1. 删除物理表
            tableMetaMapper.dropTable(tableName);
            log.info("物理表 {} 删除成功", tableName);

            // 2. 删除元数据记录
            tableMetaMapper.delete(new LambdaQueryWrapper<TableMeta>().eq(TableMeta::getTableName, tableName));
            log.info("表 {} 的元数据删除成功", tableName);
        });
    }

    /**
     * 解析Excel文件
     */
    private List<List<String>> parseExcel(InputStream inputStream) throws IOException {
        List<List<String>> result = new ArrayList<>();

        EasyExcel.read(inputStream, new ReadListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                List<String> row = new ArrayList<>();
                // 获取当前行的最大索引
                int maxIndex = data.keySet().stream().max(Integer::compareTo).orElse(-1);
                // 按顺序填充每一列
                for (int i = 0; i <= maxIndex; i++) {
                    String value = data.getOrDefault(i, "");
                    row.add(value != null ? value : "");
                }
                result.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.info("Excel解析完成，共 {} 行", result.size());
            }
            // EasyExcel 默认将第一行视为表头，需要设置 headRowNumber(0) 从第一行开始读取
        }).headRowNumber(0).sheet().doRead();

        return result;
    }

    /**
     * 生成表名
     */
    private String generateTableName(String originalFilename) {
        String baseName = originalFilename;
        // 去掉扩展名
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0)
            baseName = baseName.substring(0, dotIndex);

        // 清理非法字符
        baseName = sanitizeTableName(baseName);
        // 添加前缀
        return TABLE_PREFIX + baseName;
    }

    /**
     * 清理表名，确保符合MySQL命名规范
     */
    private String sanitizeTableName(String name) {
        if (name == null || name.trim().isEmpty())
            return "table_" + System.currentTimeMillis();

        // 转换为小写
        String sanitized = name.toLowerCase();
        // 替换非法字符为下划线
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_]", "_");
        // 确保以字母或下划线开头
        if (!sanitized.matches("^[a-zA-Z_].*"))
            sanitized = "t_" + sanitized;

        // 限制长度（MySQL表名最大64字符）
        if (sanitized.length() > 60)
            sanitized = sanitized.substring(0, 60);

        // 去掉末尾的下划线
        sanitized = sanitized.replaceAll("_+$", "");

        return sanitized;
    }

    /**
     * 验证表名是否有效
     */
    private boolean isValidTableName(String tableName) {
        return tableName != null && VALID_TABLE_NAME_PATTERN.matcher(tableName).matches();
    }

    /**
     * 生成列信息
     */
    private List<ColumnInfo> generateColumnInfo(List<String> headers) {
        List<ColumnInfo> columns = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String columnName = sanitizeColumnName(header);

            // 处理重复的列名
            String originalName = columnName;
            int suffix = 1;
            while (usedNames.contains(columnName))
                columnName = originalName + "_" + suffix++;
            usedNames.add(columnName);

            ColumnInfo column = new ColumnInfo();
            column.setIndex(i);
            column.setOriginalHeader(header);
            column.setColumnName(columnName);
            column.setDataType("VARCHAR(500)"); // 默认使用VARCHAR类型
            columns.add(column);
        }

        return columns;
    }

    /**
     * 清理列名
     */
    private String sanitizeColumnName(String name) {
        if (name == null || name.trim().isEmpty())
            return "col";

        // 转换为小写
        String sanitized = name.toLowerCase().trim();
        // 替换非法字符为下划线
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_]", "_");
        // 确保以字母开头
        if (!sanitized.matches("^[a-zA-Z].*"))
            sanitized = "col_" + sanitized;

        // 限制长度（MySQL列名最大64字符）
        if (sanitized.length() > 60)
            sanitized = sanitized.substring(0, 60);

        // 去掉连续和末尾的下划线
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("_+$", "");

        return sanitized;
    }

    /**
     * 生成建表SQL
     */
    private String generateCreateTableSql(String tableName, String description, List<ColumnInfo> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");

        // 添加自增主键
        sql.append("  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n");

        // 添加Excel列
        for (ColumnInfo column : columns) {
            sql.append("  `").append(column.getColumnName()).append("` ")
                    .append(column.getDataType())
                    .append(" DEFAULT NULL COMMENT '")
                    .append(escapeSqlComment(column.getOriginalHeader()))
                    .append("',\n");
        }

        // 添加创建时间和更新时间
        sql.append("  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n");
        sql.append("  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n");

        // 设置主键
        sql.append("  PRIMARY KEY (`id`)\n");
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='")
                .append(escapeSqlComment(description)).append("'");

        return sql.toString();
    }

    /**
     * 转义SQL注释中的特殊字符
     * <p>
     * 注意转义顺序：先转义反斜杠，再转义单引号，避免二次转义导致的错误。
     * </p>
     */
    private String escapeSqlComment(String comment) {
        if (comment == null) return "";
        // 先转义反斜杠，再转义单引号
        return comment.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * 使用 PreparedStatement 参数化批量插入数据，防止 SQL 注入
     *
     * @param tableName 表名（已通过 sanitize 校验）
     * @param columns   列信息
     * @param dataRows  数据行
     * @return 插入的总行数
     */
    private int insertData(String tableName, List<ColumnInfo> columns, List<List<String>> dataRows) {
        int batchSize = 500;
        int totalInserted = 0;

        // 构建参数化 SQL（表名和列名来自程序生成，已通过 sanitize 校验）
        String columnNames = columns.stream()
                .map(c -> "`" + c.getColumnName() + "`")
                .collect(Collectors.joining(", "));
        String placeholders = columns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        String sql = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + placeholders + ")";

        for (int i = 0; i < dataRows.size(); i += batchSize) {
            List<List<String>> batch = dataRows.subList(i, Math.min(i + batchSize, dataRows.size()));

            jdbcTemplate.batchUpdate(sql, batch, batch.size(),
                    (PreparedStatement ps, List<String> row) -> {
                        for (int j = 0; j < columns.size(); j++) {
                            String value = j < row.size() ? row.get(j) : null;
                            if (value == null || value.isEmpty())
                                ps.setNull(j + 1, Types.VARCHAR);
                            else
                                ps.setString(j + 1, value);
                        }
                    });

            totalInserted += batch.size();
        }

        return totalInserted;
    }

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        // 只有 Excel 和 CSV 文件，并且知识库类型为数据查询时支持
        if (FileType.EXCEL.equals(fileType) || FileType.CSV.equals(fileType))
            return knowledgeBaseType == KnowledgeBaseType.DATA_QUERY;
        return false;
    }

    /**
     * 安静关闭输入流
     */
    private void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 列信息内部类
     */
    @Setter
    @Getter
    public static class ColumnInfo {
        private int index;
        private String originalHeader;
        private String columnName;
        private String dataType;
    }
}
