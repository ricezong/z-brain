package cn.kong.eon.rag.retrieval;

import cn.kong.eon.config.EonProperties;
import cn.kong.eon.persistence.dto.response.RetrievalResult;
import cn.kong.eon.persistence.mapper.ChunkMapper;
import cn.kong.eon.rag.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 混合检索器实现（重写）
 *
 * <p>核心改进：</p>
 * <ol>
 *   <li><b>瘦 SQL</b>：向量/全文召回只 SELECT id, parent_id, content, doc_id, metadata（不回传 content_vector/tsv）</li>
 *   <li><b>citation 下沉</b>：rerank 完成后在此层分配 doc_1/doc_2 编号，Agent 引用不再失效</li>
 *   <li><b>JOIN 消灭 N+1</b>：parentContent + docName 一条 JOIN 批量回填</li>
 *   <li><b>rerank 3-5s 超时</b>：失败降级返回 RRF 顺序</li>
 * </ol>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultHybridRetriever implements HybridRetriever {

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final RrfFusion rrfFusion;
    private final Reranker reranker;
    private final EonProperties properties;

    @Override
    public List<RetrievalResult> retrieve(Long kbId, String vectorQuery, String textQuery) {
        long start = System.currentTimeMillis();
        EonProperties.Retrieval cfg = properties.getRetrieval();

        // 1. 向量化查询
        String queryVector = embeddingService.embed(vectorQuery);
        if (queryVector == null) {
            log.warn("[HybridRetriever] 查询向量化失败，返回空");
            return Collections.emptyList();
        }

        // 2. 双路并行召回（瘦 SQL）
        List<Map<String, Object>> vectorRows = Collections.emptyList();
        List<Map<String, Object>> fulltextRows = Collections.emptyList();
        try {
            vectorRows = chunkMapper.vectorRetrieveSkinny(kbId, queryVector, cfg.getVectorTopK());
            fulltextRows = chunkMapper.fulltextRetrieveSkinny(kbId, textQuery, cfg.getFulltextTopK());
        } catch (Exception e) {
            log.error("[HybridRetriever] 召回失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }

        log.info("[HybridRetriever] 召回: vector={}, fulltext={}", vectorRows.size(), fulltextRows.size());

        // 3. 转换为 RetrievalResult
        List<RetrievalResult> vectorResults = toResults(vectorRows, "vector");
        List<RetrievalResult> fulltextResults = toResults(fulltextRows, "fulltext");

        // 4. RRF 融合
        List<RetrievalResult> fused = rrfFusion.fuse(
                List.of(vectorResults, fulltextResults), cfg.getRrfK(), cfg.getVectorTopK());

        if (fused.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. Rerank 精排（3-5s 超时，失败降级）
        List<String> documents = fused.stream()
                .map(r -> r.getContent() != null ? r.getContent() : "")
                .toList();
        List<Reranker.RerankResult> rerankResults = reranker.rerank(vectorQuery, documents, cfg.getRerankTopN());

        List<RetrievalResult> reranked = new ArrayList<>();
        for (int i = 0; i < rerankResults.size(); i++) {
            Reranker.RerankResult rr = rerankResults.get(i);
            RetrievalResult item = fused.get(rr.index());
            item.setRerankRank(i + 1);
            reranked.add(item);
        }

        // 6. 批量回填 parentContent + docName（一条 JOIN，消灭 N+1）
        fillParentAndDoc(reranked);

        // 7. ★ citation 编号下沉：在检索层确定 doc_1/doc_2/...
        assignCitations(reranked);

        long cost = System.currentTimeMillis() - start;
        log.info("[HybridRetriever] 检索完成: rerank={}, cost={}ms", reranked.size(), cost);
        return reranked;
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("unchecked")
    private List<RetrievalResult> toResults(List<Map<String, Object>> rows, String source) {
        List<RetrievalResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            RetrievalResult r = new RetrievalResult();
            r.setChunkId(toLong(row.get("id")));
            r.setParentId(toLong(row.get("parent_id")));
            r.setContent((String) row.get("content"));
            r.setDocId(toLong(row.get("doc_id")));
            r.setMetadata((String) row.get("metadata"));
            r.setSources(List.of(source));
            results.add(r);
        }
        return results;
    }

    private void fillParentAndDoc(List<RetrievalResult> results) {
        if (results.isEmpty()) return;
        List<Long> chunkIds = results.stream().map(RetrievalResult::getChunkId).toList();
        try {
            List<Map<String, Object>> rows = chunkMapper.batchFillParentAndDoc(chunkIds);
            Map<Long, Map<String, Object>> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                map.put(toLong(row.get("chunk_id")), row);
            }
            for (RetrievalResult r : results) {
                Map<String, Object> info = map.get(r.getChunkId());
                if (info != null) {
                    r.setParentContent((String) info.get("parent_content"));
                    r.setDocName((String) info.get("doc_name"));
                }
            }
        } catch (Exception e) {
            log.warn("[HybridRetriever] 批量回填失败: {}", e.getMessage());
        }
    }

    /**
     * ★ citation 编号下沉：按 docId 分配 doc_1/doc_2/...（同文档的多个分块共享编号）
     */
    private void assignCitations(List<RetrievalResult> results) {
        Map<Long, String> docLabels = new HashMap<>();
        int counter = 1;
        for (RetrievalResult r : results) {
            if (r.getDocId() == null) continue;
            String label = docLabels.get(r.getDocId());
            if (label == null) {
                label = "doc_" + counter++;
                docLabels.put(r.getDocId(), label);
            }
            r.setCitationLabel(label);
        }
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
