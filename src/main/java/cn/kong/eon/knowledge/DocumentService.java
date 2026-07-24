package cn.kong.eon.knowledge;

import cn.kong.eon.common.exception.BusinessException;
import cn.kong.eon.common.util.CommonUtils;
import cn.kong.eon.config.EonProperties;
import cn.kong.eon.persistence.entity.Chunk;
import cn.kong.eon.persistence.entity.Document;
import cn.kong.eon.persistence.entity.KnowledgeBase;
import cn.kong.eon.persistence.mapper.DocumentMapper;
import cn.kong.eon.persistence.mapper.KnowledgeBaseMapper;
import cn.kong.eon.persistence.mapper.ChunkMapper;
import cn.kong.eon.rag.chunk.ChunkingEngine;
import cn.kong.eon.rag.parse.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档管理服务（重写：流式哈希查重 + 解析 + 分块 + 异步 embedding）
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChunkMapper chunkMapper;
    private final ChunkingEngine chunkingEngine;
    private final DocumentParser documentParser;
    private final EonProperties properties;
    private final EmbeddingTaskService embeddingTaskService;

    /**
     * 上传文档（流式 SHA-256 哈希查重）
     */
    public Document upload(MultipartFile file, Long kbId, String parseType) throws Exception {
        // 1. 校验知识库
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException("知识库不存在: " + kbId);
        }

        // 2. 流式计算哈希（DigestInputStream，大文件不进内存）
        String fileHash;
        try (InputStream is = file.getInputStream()) {
            fileHash = CommonUtils.sha256Stream(is);
        }

        // 3. 查重
        Document existing = documentMapper.selectByHash(fileHash);
        if (existing != null) {
            log.info("[DocumentService] 文档已存在（哈希查重命中）: hash={}, docId={}", fileHash, existing.getId());
            return existing;
        }

        // 4. 保存文件
        String uploadDir = properties.getDocument().getUploadDir();
        Path dir = Paths.get(uploadDir, String.valueOf(kbId));
        Files.createDirectories(dir);
        String fileName = file.getOriginalFilename();
        Path filePath = dir.resolve(fileName);
        file.transferTo(filePath.toFile());

        // 5. 创建文档记录
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFileName(fileName);
        doc.setFilePath(filePath.toString());
        doc.setFileSize(file.getSize());
        doc.setFileType(CommonUtils.getFileExtension(fileName));
        doc.setFileHash(fileHash);
        doc.setChunkSize(kb.getChunkSize());
        doc.setParseType(parseType != null ? parseType : "tika");
        doc.setStatus("pending");
        doc.setParseProgress(0);
        documentMapper.insert(doc);

        // 6. 异步解析+分块+embedding
        embeddingTaskService.processDocument(doc.getId());

        return doc;
    }

    /**
     * 解析+分块（同步入口，供 EmbeddingTaskService 异步调用）
     */
    public void parseAndChunk(Long docId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException("文档不存在: " + docId);
        }

        try {
            // 更新状态
            documentMapper.updateStatus(docId, "parsing", null);
            documentMapper.updateParseProgress(docId, 10);

            // 解析
            String content;
            try (InputStream is = Files.newInputStream(Paths.get(doc.getFilePath()))) {
                content = documentParser.parseToMarkdown(is);
            }
            documentMapper.updateParseProgress(docId, 50);

            // 分块
            List<Chunk> chunks = chunkingEngine.chunk(docId, doc.getKbId(), content, doc.getChunkSize());
            chunkMapper.batchInsert(chunks);
            chunkingEngine.linkParentIds(chunks);
            // 回填 parent_id
            for (Chunk chunk : chunks) {
                if (chunk.getParentId() != null) {
                    chunkMapper.updateParentId(chunk.getId(), chunk.getParentId());
                }
            }

            // 更新文档状态
            documentMapper.updateStatus(docId, "pending_review", null);
            documentMapper.updateParseProgress(docId, 100);
            documentMapper.updateChunkCount(docId, chunks.size());
            knowledgeBaseMapper.updateChunkCount(doc.getKbId(), chunks.size());

            log.info("[DocumentService] 解析分块完成: docId={}, chunks={}", docId, chunks.size());
        } catch (Exception e) {
            log.error("[DocumentService] 解析失败: docId={}", docId, e);
            documentMapper.updateStatus(docId, "failed", e.getMessage());
        }
    }

    public List<Document> listByKbId(Long kbId) {
        return documentMapper.selectByKbId(kbId, null);
    }

    public Document getById(Long id) {
        return documentMapper.selectById(id);
    }

    public void delete(Long id) {
        chunkMapper.deleteByDocId(id);
        documentMapper.deleteById(id);
    }
}
