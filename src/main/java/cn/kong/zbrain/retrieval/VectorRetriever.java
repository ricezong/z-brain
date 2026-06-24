package cn.kong.zbrain.retrieval;

import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.mapper.ChunkMapper;
import cn.kong.zbrain.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量检索器
 *
 * <p>基于 pgvector 进行向量相似度召回（余弦距离），Top 20。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorRetriever implements Retriever {

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    @Override
    public List<RetrievalResult> retrieve(Long kbId, String query, int topK) {
        try {
            // 1. 将查询文本向量化
            log.info("向量检索开始: kbId={}, queryLen={}, queryPreview={}",
                    kbId, query != null ? query.length() : 0,
                    query != null ? query.substring(0, Math.min(query.length(), 80)) : "null");
            String vectorStr = embeddingService.embed(query);
            if (vectorStr == null) {
                log.warn("查询向量化失败，跳过向量检索");
                return new ArrayList<>();
            }
            log.info("查询向量化完成: vectorLen={}, vectorPreview={}...",
                    vectorStr.length(), vectorStr.substring(0, Math.min(vectorStr.length(), 60)));

            // 2. 调用 Mapper 执行向量相似度检索
            List<Chunk> chunks = chunkMapper.vectorRetrieve(kbId, vectorStr, topK);
            log.info("向量检索结果: kbId={}, 命中={}条", kbId, chunks.size());

            // 3. 转换为 RetrievalResult
            List<RetrievalResult> results = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                RetrievalResult result = new RetrievalResult();
                result.setChunkId(chunk.getId());
                result.setParentId(chunk.getParentId());
                result.setContent(chunk.getContent());
                result.setDocId(chunk.getDocId());
                result.setMetadata(chunk.getMetadata());
                // 向量检索的得分使用倒数排名（由 RRF 统一计算）
                result.setScore(1.0 / (i + 1));
                result.setSources(List.of(name()));
                results.add(result);
            }
            return results;
        } catch (Exception e) {
            log.error("向量检索失败: kbId={}, query={}", kbId, query, e);
            return new ArrayList<>();
        }
    }

    @Override
    public String name() {
        return "vector";
    }
}
