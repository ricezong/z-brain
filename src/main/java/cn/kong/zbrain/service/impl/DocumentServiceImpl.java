package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.util.CommonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.cache.DocumentProgressCache;
import cn.kong.zbrain.chunk.ChunkingEngine;
import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.common.PageResult;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.request.ReviewSubmitRequest;
import cn.kong.zbrain.dto.response.DocumentProgressResponse;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.entity.Document;
import cn.kong.zbrain.entity.KnowledgeBase;
import cn.kong.zbrain.enums.DocumentStatus;
import cn.kong.zbrain.enums.ChunkStatus;
import cn.kong.zbrain.mapper.ChunkMapper;
import cn.kong.zbrain.mapper.DocumentMapper;
import cn.kong.zbrain.mapper.KnowledgeBaseMapper;
import cn.kong.zbrain.parser.DocumentParser;
import cn.kong.zbrain.parser.LlamaIndexPdfParser;
import cn.kong.zbrain.service.DocumentService;
import cn.kong.zbrain.service.EmbeddingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档服务实现
 *
 * <p>核心链路：上传 -> 异步解析 -> 父子分块 -> 待审核 -> 向量化 -> 完成</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentParser documentParser;
    private final ChunkingEngine chunkingEngine;
    private final DocumentProgressCache progressCache;
    private final ZBrainProperties properties;
    private final ObjectMapper objectMapper;
    private final EmbeddingTaskService embeddingTaskService;
    private final ObjectProvider<LlamaIndexPdfParser> llamaIndexPdfParserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long kbId, MultipartFile file, String userId, Integer chunkSize) {
        // 1. 校验知识库
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(404, "知识库不存在");
        }

        // 2. 校验文件类型
        String originalName = file.getOriginalFilename();
        String fileType = CommonUtils.getFileExtension(originalName);
        validateFileType(fileType);

        // 3. 保存文件到本地
        String uploadDir = properties.getDocument().getUploadDir();
        String fileName = System.currentTimeMillis() + "_" + originalName;
        Path filePath = Paths.get(uploadDir, fileName).toAbsolutePath();
        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage(), e);
        }

        // 4. 计算文件哈希
        String fileHash;
        try {
            fileHash = CommonUtils.sha256(new String(file.getBytes()));
        } catch (IOException e) {
            fileHash = "";
        }

        // 5. 写入文档记录
        Document document = new Document();
        document.setKbId(kbId);
        document.setFileName(originalName);
        document.setFilePath(filePath.toString());
        document.setFileSize(file.getSize());
        document.setFileType(fileType);
        document.setFileHash(fileHash);
        // 分块大小：文档未设置则使用知识库的分块大小
        document.setChunkSize(chunkSize != null ? chunkSize : kb.getChunkSize());
        document.setStatus(DocumentStatus.PENDING.getCode());
        document.setChunkCount(0);
        document.setParseProgress(0);
        document.setCreateBy(userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("originalName", originalName);
        metadata.put("uploadTime", System.currentTimeMillis());
        try {
            document.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            document.setMetadata("{}");
        }

        documentMapper.insert(document);
        knowledgeBaseMapper.updateDocCount(kbId, 1);

        // 6. 初始化进度缓存
        updateProgress(document, 0, DocumentStatus.PENDING);

        log.info("文档上传成功: docId={}, kbId={}, fileName={}", document.getId(), kbId, originalName);
        return document.getId();
    }

    @Async("parseExecutor")
    @Override
    public void parseAsync(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.error("文档不存在: docId={}", documentId);
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // 1. 状态流转为 parsing
            documentMapper.updateStatus(documentId, DocumentStatus.PARSING.getCode(), null);
            updateProgress(document, 10, DocumentStatus.PARSING);

            // 2. 解析文档为 Markdown（统一中间格式：Tika HTML → JSoup → Markdown）
            File file = new File(document.getFilePath());
            String markdown = parseDocumentToMarkdown(document, file);
            updateProgress(document, 50, DocumentStatus.PARSING);

            // 3. Markdown 语义边界父子分块（第1步：##/表格父块切分，第2步：递归字符子块切分）
            List<Chunk> chunks = chunkingEngine.chunk(markdown, documentId, document.getKbId(), document.getChunkSize());
            updateProgress(document, 80, DocumentStatus.PARSING);

            // 4. 批量写入分块
            if (!chunks.isEmpty()) {
                // 分批插入，每批 100 条
                int batchSize = 100;
                for (int i = 0; i < chunks.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, chunks.size());
                    List<Chunk> batch = chunks.subList(i, end);
                    chunkMapper.batchInsert(batch);
                }
            }

            // 5. 更新文档状态为待审核
            int chunkCount = chunkMapper.countByDocId(documentId);
            documentMapper.updateStatus(documentId, DocumentStatus.PENDING_REVIEW.getCode(), null);
            documentMapper.updateChunkCount(documentId, chunkCount);
            knowledgeBaseMapper.updateChunkCount(document.getKbId(), chunkCount);

            // 同步 chunkCount 到 document 对象，确保进度缓存写入最新值
            document.setChunkCount(chunkCount);
            updateProgress(document, 100, DocumentStatus.PENDING_REVIEW);

            long cost = System.currentTimeMillis() - startTime;
            log.info("文档解析完成: docId={}, chunkCount={}, cost={}ms",
                    documentId, chunkCount, cost);

        } catch (Exception e) {
            log.error("文档解析失败: docId={}", documentId, e);
            documentMapper.updateStatus(documentId, DocumentStatus.FAILED.getCode(), e.getMessage());
            updateProgress(document, 0, DocumentStatus.FAILED, e.getMessage());
        }
    }

    @Override
    public DocumentProgressResponse getProgress(Long documentId) {
        // 优先从缓存读取
        DocumentProgressResponse cached = progressCache.get(documentId);
        if (cached != null) {
            return cached;
        }
        // 缓存未命中，从数据库读取
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(404, "文档不存在");
        }
        DocumentProgressResponse resp = new DocumentProgressResponse();
        resp.setDocumentId(document.getId());
        resp.setStatus(document.getStatus());
        resp.setProgress(document.getParseProgress());
        resp.setErrorMessage(document.getErrorMessage());
        resp.setChunkCount(document.getChunkCount());
        return resp;
    }

    @Override
    public Document getById(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return document;
    }

    @Override
    public PageResult<Document> list(Long kbId, String fileName, String status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<Document> list = documentMapper.selectList(kbId, fileName, status, offset, pageSize);
        long total = documentMapper.countList(kbId, fileName, status);
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(404, "文档不存在");
        }
        // 1. 删除分块
        chunkMapper.deleteByDocId(id);
        // 2. 删除文档
        documentMapper.deleteById(id);
        // 3. 更新知识库计数
        knowledgeBaseMapper.updateDocCount(document.getKbId(), -1);
        knowledgeBaseMapper.updateChunkCount(document.getKbId(), -document.getChunkCount());
        // 4. 删除进度缓存
        progressCache.remove(id);
        // 5. 删除本地文件
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            log.warn("删除本地文件失败: {}", document.getFilePath());
        }
        log.info("文档删除成功: docId={}", id);
    }

    @Override
    public void triggerEmbedding(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(404, "文档不存在");
        }
        if (!DocumentStatus.PENDING_REVIEW.getCode().equals(document.getStatus())) {
            throw new BusinessException("文档状态不允许触发向量化: " + document.getStatus());
        }
        documentMapper.updateStatus(documentId, DocumentStatus.EMBEDDING.getCode(), null);
        updateProgress(document, 0, DocumentStatus.EMBEDDING);
        embeddingTaskService.embedAsync(documentId);
        log.info("文档触发向量化: docId={}", documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long documentId, ReviewSubmitRequest request) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(404, "文档不存在");
        }

        // 1. 处理删除
        if (request.getDeleted() != null && !request.getDeleted().isEmpty()) {
            for (Long chunkId : request.getDeleted()) {
                chunkMapper.deleteById(chunkId);
            }
            log.info("审核删除分块: docId={}, count={}", documentId, request.getDeleted().size());
        }

        // 2. 处理修改
        if (request.getModified() != null) {
            for (ReviewSubmitRequest.ChunkDiffItem item : request.getModified()) {
                Chunk chunk = new Chunk();
                chunk.setId(item.getId());
                chunk.setContent(item.getContent());
                chunk.setTokenCount(item.getTokenCount());
                chunk.setMetadata(item.getMetadata());
                if (item.getParentId() != null) {
                    chunk.setParentId(item.getParentId());
                }
                chunkMapper.update(chunk);
            }
        }

        // 3. 处理新增
        if (request.getAdded() != null && !request.getAdded().isEmpty()) {
            List<Chunk> newChunks = new ArrayList<>();
            for (ReviewSubmitRequest.ChunkDiffItem item : request.getAdded()) {
                Chunk chunk = new Chunk();
                chunk.setDocId(documentId);
                chunk.setKbId(document.getKbId());
                chunk.setParentId(item.getParentId());
                chunk.setChunkType(item.getChunkType());
                chunk.setContent(item.getContent());
                chunk.setTokenCount(item.getTokenCount());
                chunk.setStatus(ChunkStatus.DRAFT.getCode());
                chunk.setMetadata(item.getMetadata());
                newChunks.add(chunk);
            }
            // 分批插入
            int batchSize = 100;
            for (int i = 0; i < newChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, newChunks.size());
                chunkMapper.batchInsert(newChunks.subList(i, end));
            }
        }

        // 4. 更新文档状态为向量化中
        int chunkCount = chunkMapper.countByDocId(documentId);
        documentMapper.updateChunkCount(documentId, chunkCount);
        knowledgeBaseMapper.updateChunkCount(document.getKbId(),
                chunkCount - document.getChunkCount());

        documentMapper.updateStatus(documentId, DocumentStatus.EMBEDDING.getCode(), null);
        document.setChunkCount(chunkCount);
        updateProgress(document, 0, DocumentStatus.EMBEDDING);

        // 触发异步向量化
        embeddingTaskService.embedAsync(documentId);

        log.info("审核提交完成: docId={}, chunkCount={}", documentId, chunkCount);
    }

    /**
     * 将文档解析为 Markdown（统一格式，保留语义结构）。
     *
     * <p>路由策略：
     * <ul>
     *   <li>PDF + LlamaIndex 已启用 → LlamaIndex Cloud Parsing（原生 Markdown）</li>
     *   <li>LlamaIndex 失败 → 回退到 Tika HTML → JSoup → Markdown</li>
     *   <li>其余格式 → Tika HTML 解析 + JSoup 转 Markdown</li>
     * </ul>
     *
     * @param document 文档实体
     * @param file     本地文件
     * @return Markdown 格式文本（含 ## 标题、| | 表格）
     */
    private String parseDocumentToMarkdown(Document document, File file) {
        String fileType = document.getFileType();
        boolean isPdf = "pdf".equalsIgnoreCase(fileType);

        // PDF 且 LlamaIndex 已启用 → LlamaIndex（原生 Markdown），失败回退
        LlamaIndexPdfParser llamaIndexParser = llamaIndexPdfParserProvider.getIfAvailable();
        if (isPdf && llamaIndexParser != null) {
            log.info("使用 LlamaIndex 解析 PDF (Markdown): docId={}", document.getId());
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                return llamaIndexParser.parse(fileBytes, document.getFileName());
            } catch (Exception e) {
                log.warn("LlamaIndex 解析失败，回退到 Tika→Markdown: docId={}", document.getId());
            }
        }

        // Tika HTML → JSoup → Markdown（统一归一化管线）
        log.info("使用 Tika→Markdown 解析文档: docId={}, fileType={}", document.getId(), fileType);
        try (FileInputStream fis = new FileInputStream(file)) {
            return documentParser.parseToMarkdown(fis);
        } catch (Exception e) {
            log.warn("Tika→Markdown 解析失败，回退到纯文本包装: docId={}", document.getId());
            try (FileInputStream fis = new FileInputStream(file)) {
                return wrapPlainTextAsMarkdown(documentParser.parse(fis));
            } catch (IOException ioe) {
                throw new BusinessException("读取文件失败: " + ioe.getMessage(), ioe);
            }
        }
    }

    /**
     * 将纯文本包装为简单 Markdown（每段落空行分隔）
     */
    private String wrapPlainTextAsMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            sb.append(trimmed).append("\n\n");
        }
        return sb.toString().trim();
    }

    private void updateProgress(Document document, int progress, DocumentStatus status) {
        updateProgress(document, progress, status, null);
    }

    private void updateProgress(Document document, int progress, DocumentStatus status, String errorMessage) {
        DocumentProgressResponse resp = new DocumentProgressResponse();
        resp.setDocumentId(document.getId());
        resp.setStatus(status.getCode());
        resp.setProgress(progress);
        resp.setErrorMessage(errorMessage);
        resp.setChunkCount(document.getChunkCount());
        progressCache.update(document.getId(), resp);
    }

    private void validateFileType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            throw new BusinessException("无法识别文件类型");
        }
        String allowed = properties.getDocument().getAllowedTypes();
        if (!allowed.contains(fileType.toLowerCase())) {
            throw new BusinessException("不支持的文件类型: " + fileType);
        }
    }
}
