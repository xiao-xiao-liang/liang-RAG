package com.liang.rag.common.enums;

import lombok.Getter;

/**
 * 文件类型枚举
 * <p>
 * 通过 {@link #fromExtension(String)} 将文件扩展名映射为统一的文件类型。
 * </p>
 */
@Getter
public enum FileType {
    PDF("pdf"),
    DOC("doc"),
    TXT("txt"),
    HTML("html"),
    MARKDOWN("markdown"),
    CSV("csv"),
    EXCEL("excel");

    private final String type;

    FileType(String type) {
        this.type = type;
    }

    /**
     * 根据文件扩展名获取对应的文件类型
     * <p>
     * 支持多种扩展名映射到同一文件类型（如 xlsx/xls → EXCEL，doc/docx → DOC）。
     * 无法识别的扩展名默认返回 TXT。
     * </p>
     *
     * @param extension 文件扩展名（不含点号），大小写不敏感
     * @return 对应的文件类型
     */
    public static FileType fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return TXT;
        }
        return switch (extension.toLowerCase()) {
            case "pdf" -> PDF;
            case "doc", "docx" -> DOC;
            case "xlsx", "xls" -> EXCEL;
            case "csv" -> CSV;
            case "md", "markdown" -> MARKDOWN;
            case "html", "htm" -> HTML;
            case "txt" -> TXT;
            default -> TXT;
        };
    }
}