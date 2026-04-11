package com.liang.rag.parser.factory;

import com.liang.rag.common.enums.FileType;
import com.liang.rag.common.enums.KnowledgeBaseType;
import com.liang.rag.parser.FileProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件处理服务工厂
 * <p>
 * 基于策略模式，根据文件类型和知识库类型自动路由到对应的 {@link FileProcessService} 实现。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class FileProcessServiceFactory {

    private final List<FileProcessService> fileProcessServices;

    /**
     * 根据文件类型和知识库类型获取对应的文件处理服务
     *
     * @param fileType          文件类型
     * @param knowledgeBaseType 知识库类型
     * @return 匹配的文件处理服务，未找到时返回 null
     */
    public FileProcessService get(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        for (FileProcessService fileProcessService : fileProcessServices) {
            if (fileProcessService.supports(fileType, knowledgeBaseType)) {
                return fileProcessService;
            }
        }
        return null;
    }
}
