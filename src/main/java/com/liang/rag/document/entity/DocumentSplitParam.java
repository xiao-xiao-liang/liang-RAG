package com.liang.rag.document.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档切分参数 DTO
 */
@Data
public class DocumentSplitParam {

    /**
     * 切分类型（TITLE / LENGTH / REGEX / SMART / SEPARATOR）
     */
    @NotBlank(message = "切分类型不能为空")
    private String splitType;

    /**
     * 最大块大小（字符数）
     */
    @NotNull(message = "最大块大小不能为空")
    @Min(value = 100, message = "最大块大小不能小于100")
    @Max(value = 100000, message = "最大块大小不能超过100000")
    private Integer chunkSize;

    /**
     * 重叠大小
     */
    @Min(value = 0, message = "重叠大小不能为负数")
    private Integer overlap;

    /**
     * 分隔符
     */
    private String separator;

    /**
     * 正则表达式
     */
    private String regex;

    /**
     * 标题级别（按标题切分时使用，1-6）
     */
    @Min(value = 1, message = "标题级别最小为1")
    @Max(value = 6, message = "标题级别最大为6")
    private Integer titleLevel;
}
