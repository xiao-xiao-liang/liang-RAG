package com.liang.rag.document.service;

import com.liang.rag.common.constant.ContentTypeConstant;
import com.liang.rag.common.convention.exception.RemoteException;
import com.liang.rag.common.convention.exception.ServiceException;
import com.liang.rag.common.enums.DocumentStatus;
import com.liang.rag.config.DocumentProcessProperties;
import com.liang.rag.config.MineruProperties;
import com.liang.rag.document.entity.KnowledgeDocument;
import com.liang.rag.document.mapper.KnowledgeDocumentMapper;
import com.liang.rag.storage.FileStorageStrategy;
import com.liang.rag.mq.event.DocumentConvertEvent;
import com.liang.rag.mq.producer.DocumentProcessProducer;
import com.liang.rag.mq.transaction.DocumentTransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PDF文档处理服务
 * <p>
 * 负责调用 MinerU API 将 PDF 文档转换为 Markdown/ZIP 格式，
 * 解压 ZIP 后将图片和 Markdown 分别上传至 MinIO，
 * 并对 Markdown 中的图片标签进行路径替换和 LLM 描述生成。
 * </p>
 * <p>PDF 转换操作通过 {@code @Async} 异步执行，避免阻塞上传请求。</p>
 * <p>
 * 处理流程：
 * <ol>
 *     <li>调用 MinerU API 获取 ZIP 格式解析结果</li>
 *     <li>将 ZIP 写入本地临时目录</li>
 *     <li>解压 ZIP，提取 Markdown 文件和图片</li>
 *     <li>将图片上传到 MinIO，建立路径映射</li>
 *     <li>替换 Markdown 中的图片相对路径为 MinIO URL</li>
 *     <li>对每个图片标签调用 LLM 生成描述（当前 Mock）</li>
 *     <li>将处理后的 Markdown 上传到 MinIO</li>
 *     <li>将 Markdown 的 MinIO URL 保存到 KnowledgeDocument.convertedDocUrl</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PDFProcessService {

    private static final String CONVERTED_FILE_DIR = "converted/";

    /** 支持的图片文件扩展名集合 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".tiff"
    );

    /** Markdown 图片标签正则：匹配 ![alt text](image path) */
    private static final Pattern IMAGE_TAG_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");

    private final FileStorageStrategy fileStorageStrategy;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final MineruProperties mineruProperties;
    private final DocumentProcessProperties documentProcessProperties;
    private final ImageDescriptionService imageDescriptionService;
    private final DocumentProcessProducer documentProcessProducer;

    /**
     * 异步处理文档转换（PDF → ZIP → 解压 → 处理 → 上传 MinIO）
     * <p>
     * 完整处理流程：
     * 1. 更新文档状态为 CONVERTING
     * 2. 调用 MinerU API 解析 PDF，获取 ZIP 格式结果
     * 3. 将 ZIP 写入本地磁盘并解压
     * 4. 上传解压后的图片到 MinIO，建立路径映射
     * 5. 处理所有 MD 文件的图片标签（路径替换 + LLM 描述生成）
     * 6. 将处理后的 MD 上传到 MinIO
     * 7. 更新文档状态为 CONVERTED，保存 MD 的 MinIO URL
     * </p>
     *
     * @param document    文档实体
     * @param inputStream PDF 文件输入流
     */
    public void processDocument(KnowledgeDocument document, InputStream inputStream) {
        log.info("开始异步处理文档转换, docTitle: {}", document.getDocTitle());

        // 更新状态为转换中
        document.setStatus(DocumentStatus.CONVERTING);
        knowledgeDocumentMapper.updateById(document);

        Path tempDir = null;
        try {
            // 哈希编码文件名，避免中文路径问题
            String encodedName = document.getDocTitle() + document.getDocTitle().hashCode();

            // 步骤1：调用 MinerU API 获取 ZIP 格式结果
            byte[] zipBytes = callMineruParseApi(encodedName, inputStream);

            // 使用 docId 构建 MinIO 路径和本地目录名，规避中文编码问题
            String baseName = "doc_" + document.getDocId();

            // 步骤2：创建临时工作目录
            tempDir = createTempDir(baseName);

            // 步骤3：将 ZIP 写入本地磁盘
            Path zipFile = saveZipToLocal(zipBytes, tempDir, baseName);
            log.info("ZIP 已保存到本地: {}", zipFile);

            // 步骤4：解压 ZIP 到本地目录
            Path extractDir = tempDir.resolve("extract");
            unzipToLocal(zipFile, extractDir);
            log.info("ZIP 已解压到: {}", extractDir);

            // 步骤5：上传解压后的图片到 MinIO，建立 本地相对路径 → MinIO URL 映射
            Map<String, String> imageUrlMap = uploadImagesToMinio(extractDir, baseName);
            log.info("已上传 {} 张图片到 MinIO", imageUrlMap.size());

            // 步骤6：处理所有 MD 文件——替换图片路径为 MinIO URL，并为图片生成 LLM 描述
            List<Path> mdFiles = findFilesByExtension(extractDir, Set.of(".md"));
            String convertedUrl = null;

            for (int i = 0; i < mdFiles.size(); i++) {
                Path mdFile = mdFiles.get(i);
                // 读取 MD 内容，替换图片路径，生成图片描述
                String processedContent = processMarkdownContent(mdFile, extractDir, imageUrlMap);

                // 上传处理后的 MD 到 MinIO（使用 docId 命名，规避中文乱码）
                String mdFileName = mdFiles.size() == 1
                        ? baseName + ".md"
                        : baseName + "_" + i + ".md";
                String mdObjectName = CONVERTED_FILE_DIR + baseName + "/" + mdFileName;
                String mdUrl = fileStorageStrategy.upload(
                        mdObjectName,
                        processedContent.getBytes(StandardCharsets.UTF_8),
                        ContentTypeConstant.TEXT_MARKDOWN
                );
                log.info("MD 文件已上传到 MinIO: {}", mdUrl);

                // 取第一个 MD 文件的 URL 作为文档的 convertedDocUrl
                if (convertedUrl == null) {
                    convertedUrl = mdUrl;
                }
            }

            // 步骤7-8：使用事务消息保证「DB UPDATE → CONVERTED」和「MQ 发送」的原子性
            document.setStatus(DocumentStatus.CONVERTED);
            document.setConvertedDocUrl(convertedUrl);

            DocumentConvertEvent event = DocumentConvertEvent.builder()
                    .documentId(document.getDocId())
                    .build();
            DocumentTransactionContext txCtx = DocumentTransactionContext.builder()
                    .action(DocumentTransactionContext.TransactionAction.UPDATE)
                    .document(document)
                    .build();

            documentProcessProducer.sendTransactionalConvertEvent(event, txCtx);
            log.info("文档转换完成, docTitle: {}, convertedUrl: {}", document.getDocTitle(), convertedUrl);

        } catch (Exception e) {
            log.error("文档转换失败, docTitle: {}", document.getDocTitle(), e);
            // 转换失败，状态回滚为 UPLOADED，允许 MQ 重试
            document.setStatus(DocumentStatus.UPLOADED);
            knowledgeDocumentMapper.updateById(document);
            // 重新抛出异常，让 Consumer 感知失败并触发 RocketMQ 重试
            throw new RuntimeException("文档转换失败: " + document.getDocTitle(), e);
        } finally {
            closeQuietly(inputStream);
            // 无论成功失败，均清理本地临时文件
            if (tempDir != null) cleanupTempFiles(tempDir);
        }
    }

    // ==================== ZIP 文件处理 ====================

    /**
     * 将 ZIP 字节数组写入本地磁盘
     *
     * @param zipBytes ZIP 文件内容
     * @param tempDir  临时工作目录
     * @param baseName 文档基础名称
     * @return ZIP 文件的本地路径
     */
    private Path saveZipToLocal(byte[] zipBytes, Path tempDir, String baseName) throws IOException {
        Path zipFile = tempDir.resolve(baseName + ".zip");
        Files.write(zipFile, zipBytes);
        return zipFile;
    }

    /**
     * 将 ZIP 文件解压到指定目录
     * <p>包含 ZipSlip 安全校验，防止恶意 ZIP 中的路径穿越攻击</p>
     *
     * @param zipFile    ZIP 文件路径
     * @param extractDir 解压目标目录
     */
    private void unzipToLocal(Path zipFile, Path extractDir) throws IOException {
        Files.createDirectories(extractDir);
        // 使用绝对路径进行 ZipSlip 校验，避免相对路径 normalize 导致的前缀不匹配
        Path normalizedExtractDir = extractDir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipFile)), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = normalizedExtractDir.resolve(entry.getName()).normalize();
                // ZipSlip 安全校验：确保解压路径不会逃出目标目录
                if (!entryPath.startsWith(normalizedExtractDir)) {
                    throw new ServiceException("ZIP 解压路径不安全（ZipSlip）: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    // ==================== 文件扫描与上传 ====================

    /**
     * 按扩展名递归查找目录下的所有文件
     *
     * @param dir        搜索起始目录
     * @param extensions 需要匹配的文件扩展名集合（如 .md、.png）
     * @return 匹配的文件路径列表
     */
    private List<Path> findFilesByExtension(Path dir, Set<String> extensions) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.exists(dir)) {
            return result;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return extensions.stream().anyMatch(name::endsWith);
                    })
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * 上传解压目录中的所有图片到 MinIO
     * <p>
     * 扫描解压目录下的所有图片文件，逐个上传到 MinIO，
     * 并返回 图片相对路径 → MinIO URL 的映射，供后续 MD 处理使用。
     * </p>
     *
     * @param extractDir 解压根目录
     * @param baseName   文档基础名称（用于构建 MinIO 存储路径）
     * @return 图片相对路径 → MinIO URL 的映射表
     */
    private Map<String, String> uploadImagesToMinio(Path extractDir, String baseName) throws IOException {
        Map<String, String> imageUrlMap = new HashMap<>();
        List<Path> imageFiles = findFilesByExtension(extractDir, IMAGE_EXTENSIONS);

        for (Path imageFile : imageFiles) {
            // 计算相对于解压目录的路径（统一使用 / 分隔符，兼容 Windows）
            String relativePath = extractDir.relativize(imageFile).toString().replace("\\", "/");
            // 剥离 MinerU ZIP 中的中文顶层目录，仅保留 images/xxx.png 部分
            String cleanRelativePath = stripTopLevelDir(relativePath);
            String objectName = CONVERTED_FILE_DIR + baseName + "/" + cleanRelativePath;
            String contentType = guessImageContentType(imageFile.getFileName().toString());

            byte[] imageBytes = Files.readAllBytes(imageFile);
            String imageUrl = fileStorageStrategy.upload(objectName, imageBytes, contentType);

            // key 使用原始本地路径，确保 MD 处理时路径匹配
            imageUrlMap.put(relativePath, imageUrl);
            log.debug("图片已上传: {} → {}", relativePath, imageUrl);
        }
        return imageUrlMap;
    }

    /**
     * 处理 Markdown 文件内容：替换图片相对路径为 MinIO URL，并为无描述的图片生成 LLM 描述
     * <p>
     * 处理逻辑：
     * 1. 读取 MD 文件内容
     * 2. 匹配所有 {@code ![alt](path)} 图片标签
     * 3. 将图片相对路径替换为 MinIO URL
     * 4. 如果 alt text 为空，调用 {@link ImageDescriptionService} 生成描述
     * </p>
     *
     * @param mdFile      MD 文件路径
     * @param extractDir  解压根目录（用于路径解析）
     * @param imageUrlMap 图片相对路径 → MinIO URL 的映射表
     * @return 处理后的 MD 内容
     */
    private String processMarkdownContent(Path mdFile, Path extractDir, Map<String, String> imageUrlMap) throws IOException {
        String content = Files.readString(mdFile, StandardCharsets.UTF_8);

        // MD 文件所在目录，用于将图片引用的相对路径解析为绝对路径
        Path mdParentDir = mdFile.getParent();

        Matcher matcher = IMAGE_TAG_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String altText = matcher.group(1);
            String imagePath = matcher.group(2).trim();

            // 基于 MD 文件所在目录解析图片的相对路径，再转为相对于解压根目录的路径
            Path resolvedImagePath = mdParentDir.resolve(imagePath).normalize();
            String relativeToExtract = extractDir.relativize(resolvedImagePath)
                    .toString().replace("\\", "/");

            // 在映射表中查找对应的 MinIO URL
            String minioUrl = imageUrlMap.get(relativeToExtract);

            if (minioUrl != null) {
                // 如果 alt text 为空，调用 LLM 生成图片描述
                if (altText == null || altText.isBlank()) {
                    String fileName = Paths.get(imagePath).getFileName().toString();
                    altText = imageDescriptionService.generateDescription(resolvedImagePath, fileName);
                    log.debug("为图片生成描述: {} → {}", fileName, altText);
                }
                // 替换为 ![描述](MinIO URL)
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement("![" + altText + "](" + minioUrl + ")"));
            } else {
                // 未找到对应图片，保持原始标签不变
                log.warn("MD 中引用的图片未在解压目录中找到: {}", imagePath);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ==================== 工具方法 ====================

    /**
     * 移除路径中的第一级目录
     * <p>
     * MinerU 生成的 ZIP 中，顶层目录通常使用中文文件名（如 垃圾回收/images/0.png），
     * 上传到 MinIO 时需要剥离这一层，仅保留后续路径（如 images/0.png）。
     * </p>
     *
     * @param relativePath 相对路径（使用 / 分隔）
     * @return 去掉第一级目录后的路径
     */
    private String stripTopLevelDir(String relativePath) {
        int firstSlash = relativePath.indexOf('/');
        if (firstSlash >= 0 && firstSlash < relativePath.length() - 1) {
            return relativePath.substring(firstSlash + 1);
        }
        return relativePath;
    }

    /**
     * 创建文档处理的临时工作目录
     * <p>目录名包含时间戳，避免并发处理时的目录冲突</p>
     *
     * @param baseName 文档基础名称
     * @return 临时工作目录路径
     */
    private Path createTempDir(String baseName) throws IOException {
        Path baseDir = Paths.get(documentProcessProperties.getTempDir());
        Path tempDir = baseDir.resolve(baseName + "_" + System.currentTimeMillis());
        Files.createDirectories(tempDir);
        return tempDir;
    }

    /**
     * 根据文件扩展名推断图片的 MIME 类型
     *
     * @param fileName 文件名
     * @return 对应的 MIME 类型字符串
     */
    private String guessImageContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return ContentTypeConstant.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ContentTypeConstant.IMAGE_JPEG;
        }
        if (lower.endsWith(".gif")) {
            return ContentTypeConstant.IMAGE_GIF;
        }
        if (lower.endsWith(".svg")) {
            return ContentTypeConstant.IMAGE_SVG;
        }
        if (lower.endsWith(".webp")) {
            return ContentTypeConstant.IMAGE_WEBP;
        }
        return ContentTypeConstant.APPLICATION_OCTET_STREAM;
    }

    /**
     * 递归删除临时工作目录及其所有内容
     *
     * @param dir 待删除的目录
     */
    private void cleanupTempFiles(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @NotNull
                    @Override
                    public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @NotNull
                    @Override
                    public FileVisitResult postVisitDirectory(@NotNull Path d, IOException exc) throws IOException {
                        Files.deleteIfExists(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
                log.info("临时目录已清理: {}", dir);
            }
        } catch (IOException e) {
            log.warn("清理临时目录失败: {}", dir, e);
        }
    }

    // ==================== MinerU API 调用 ====================

    /**
     * 调用 MinerU 文件解析 API，获取 ZIP 格式响应
     * <p>使用 Apache HttpClient 5，支持大文件流式上传和长超时控制</p>
     *
     * @param fileName   文件名（编码后）
     * @param fileStream PDF 文件输入流
     * @return ZIP 文件字节数组
     */
    private byte[] callMineruParseApi(String fileName, InputStream fileStream) {
        String url = mineruProperties.getUrl() + "/file_parse";

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(mineruProperties.getConnectTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(mineruProperties.getResponseTimeout()))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");

            // 构建 multipart 请求体：启用 ZIP 格式和返回图片
            HttpEntity multipartEntity = MultipartEntityBuilder.create()
                    .addBinaryBody("files", fileStream, ContentType.APPLICATION_OCTET_STREAM, fileName)
                    .addTextBody("backend", "pipeline")
                    .addTextBody("response_format_zip", "true")
                    .addTextBody("return_images", "true")
                    .addTextBody("return_model_output", "false")
                    .addTextBody("return_middle_json", "false")
                    .build();

            httpPost.setEntity(multipartEntity);
            log.info("调用 MinerU 解析接口（ZIP 模式）: {}", url);

            try (var response = httpClient.executeOpen(null, httpPost, null)) {
                int statusCode = response.getCode();
                log.info("MinerU 响应状态码: {}", statusCode);

                HttpEntity responseEntity = response.getEntity();
                if (statusCode == 200 && responseEntity != null) {
                    byte[] zipBytes = EntityUtils.toByteArray(responseEntity);
                    log.info("MinerU 解析成功, ZIP 大小: {} bytes", zipBytes.length);
                    return zipBytes;
                } else {
                    String body = responseEntity != null ? EntityUtils.toString(responseEntity, "UTF-8") : "";
                    throw new RemoteException("MinerU 解析失败: HTTP " + statusCode + ", " + body);
                }
            }
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("调用 MinerU 解析接口异常: " + e.getMessage());
        } finally {
            closeQuietly(fileStream);
        }
    }

    /**
     * 安静关闭输入流
     */
    private void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
        }
    }
}
