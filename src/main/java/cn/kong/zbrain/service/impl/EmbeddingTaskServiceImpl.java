package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.cache.DocumentProgressCache;
import cn.kong.zbrain.config.DashScopeConfig;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.response.DocumentProgressResponse;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.entity.Document;
import cn.kong.zbrain.enums.ChunkStatus;
import cn.kong.zbrain.enums.DocumentStatus;
import cn.kong.zbrain.mapper.ChunkMapper;
import cn.kong.zbrain.mapper.DocumentMapper;
import cn.kong.zbrain.mapper.KnowledgeBaseMapper;
import cn.kong.zbrain.service.EmbeddingService;
import cn.kong.zbrain.service.EmbeddingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化任务服务实现
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>查询文档下所有 status=draft 的子块</li>
 *   <li>先检查 Redis Embedding 缓存，命中直接使用</li>
 *   <li>未命中的批量调用百炼 SDK text-embedding-v4</li>
 *   <li>批量更新子块的 vector 字段</li>
 *   <li>父子块状态更新为 active，文档状态更新为 success</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingTaskServiceImpl implements EmbeddingTaskService {

    private final ChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingService embeddingService;
    private final DashScopeConfig dashScopeConfig;
    private final ZBrainProperties properties;
    private final DocumentProgressCache progressCache;

    @Override
    @Async("embeddingExecutor")
    public void embedAsync(Long documentId) {
        embed(documentId);
    }

    @Override
    public void embed(Long documentId) {
        log.info("开始向量化: docId={}", documentId);
        long startTime = System.currentTimeMillis();

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.error("文档不存在: docId={}", documentId);
            return;
        }

        try {
            // 1. 查询待向量化的子块
            List<Chunk> chunks = chunkMapper.selectChunksForEmbedding(documentId);
            if (chunks.isEmpty()) {
                log.warn("没有待向量化的子块: docId={}", documentId);
                finalizeSuccess(document);
                return;
            }

            // 2. 批量向量化
            int batchSize = dashScopeConfig.getEmbeddingBatchSize();
            int total = chunks.size();

            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<Chunk> batch = chunks.subList(i, end);

                // 提取文本
                List<String> texts = batch.stream().map(Chunk::getContent).toList();

                // 调用向量化
                List<String> vectors = embeddingService.embedBatch(texts);

                // 更新向量
                List<Chunk> toUpdate = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    Chunk chunk = batch.get(j);
                    chunk.setContentVector(vectors.get(j));
                    chunk.setStatus(ChunkStatus.ACTIVE.getCode());
                    toUpdate.add(chunk);
                }
                chunkMapper.batchUpdateVector(toUpdate);

                // 更新进度
                int progress = (int) ((double) end / total * 100);
                updateProgress(document, progress, DocumentStatus.EMBEDDING);
                log.debug("向量化进度: docId={}, {}/{}", documentId, end, total);
            }

            // 3. 父块状态更新为 active
            List<Chunk> allChunks = chunkMapper.selectByDocId(documentId);
            List<Long> parentIds = allChunks.stream()
                    .filter(c -> "parent".equals(c.getChunkType()))
                    .map(Chunk::getId)
                    .toList();
            if (!parentIds.isEmpty()) {
                chunkMapper.batchUpdateStatus(parentIds, ChunkStatus.ACTIVE.getCode());
            }

            // 4. 文档状态更新为 success
            finalizeSuccess(document);

            long cost = System.currentTimeMillis() - startTime;
            log.info("向量化完成: docId={}, chunkCount={}, cost={}ms", documentId, total, cost);

        } catch (Exception e) {
            log.error("向量化失败: docId={}", documentId, e);
            documentMapper.updateStatus(documentId, DocumentStatus.FAILED.getCode(), e.getMessage());
            updateProgress(document, 0, DocumentStatus.FAILED, e.getMessage());
        }
    }

    private void finalizeSuccess(Document document) {
        int chunkCount = chunkMapper.countByDocId(document.getId());
        documentMapper.updateChunkCount(document.getId(), chunkCount);
        documentMapper.updateStatus(document.getId(), DocumentStatus.SUCCESS.getCode(), null);
        document.setChunkCount(chunkCount);
        updateProgress(document, 100, DocumentStatus.SUCCESS);
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
}
