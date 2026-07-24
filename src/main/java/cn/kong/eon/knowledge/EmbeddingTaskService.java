package cn.kong.eon.knowledge;

import cn.kong.eon.persistence.entity.Chunk;
import cn.kong.eon.persistence.mapper.ChunkMapper;
import cn.kong.eon.persistence.mapper.DocumentMapper;
import cn.kong.eon.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 异步 Embedding 任务服务
 *
 * <p>文档审核通过后触发：批量向量化子块 + 更新状态。</p>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingTaskService {

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final DocumentService documentService;

    /**
     * 异步处理文档（解析 → 分块 → 等待审核）
     */
    @Async
    public void processDocument(Long docId) {
        documentService.parseAndChunk(docId);
    }

    /**
     * 异步 embedding（审核通过后触发）
     */
    @Async
    public void embedDocument(Long docId) {
        try {
            documentMapper.updateStatus(docId, "embedding", null);

            List<Chunk> chunks = chunkMapper.selectChunksForEmbedding(docId);
            if (chunks.isEmpty()) {
                log.info("[EmbeddingTask] 无需向量化的分块: docId={}", docId);
                documentMapper.updateStatus(docId, "success", null);
                return;
            }

            log.info("[EmbeddingTask] 开始向量化: docId={}, chunks={}", docId, chunks.size());

            // 批量向量化
            List<String> texts = chunks.stream().map(Chunk::getContent).toList();
            List<String> vectors = embeddingService.embedBatch(texts);

            // 回填向量
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setContentVector(vectors.get(i));
                chunks.get(i).setStatus("active");
            }
            chunkMapper.batchUpdateVector(chunks);

            documentMapper.updateStatus(docId, "success", null);
            log.info("[EmbeddingTask] 向量化完成: docId={}", docId);
        } catch (Exception e) {
            log.error("[EmbeddingTask] 向量化失败: docId={}", docId, e);
            documentMapper.updateStatus(docId, "failed", e.getMessage());
        }
    }
}
