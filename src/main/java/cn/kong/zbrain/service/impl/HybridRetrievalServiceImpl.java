package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.mapper.ChunkMapper;
import cn.kong.zbrain.retrieval.FullTextRetriever;
import cn.kong.zbrain.retrieval.RRFFusion;
import cn.kong.zbrain.retrieval.VectorRetriever;
import cn.kong.zbrain.service.HybridRetrievalService;
import cn.kong.zbrain.service.RerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 混合检索服务实现
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>两路并行召回：向量（Top 20）+ 全文（Top 20），通过 retrievalExecutor 线程池并行执行</li>
 *   <li>RRF 融合：倒数秩融合算法，k=60，取 Top 10</li>
 *   <li>Rerank 精排：百炼 qwen3-rerank，取 Top 5</li>
 *   <li>降级策略：Rerank 失败则使用 RRF 融合结果</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalServiceImpl implements HybridRetrievalService {

    private final VectorRetriever vectorRetriever;
    private final FullTextRetriever fullTextRetriever;
    private final RRFFusion rrfFusion;
    private final RerankService rerankService;
    private final ChunkMapper chunkMapper;
    private final ZBrainProperties properties;

    @Qualifier("retrievalExecutor")
    private final Executor retrievalExecutor;

    /** ThreadLocal 存储最近一次检索过程信息，用于日志记录 */
    private final ThreadLocal<RetrievalInfo> lastInfo = new ThreadLocal<>();

    @Override
    public List<RetrievalResult> hybridRetrieve(Long kbId, String vectorQuery, String textQuery) {
        long start = System.currentTimeMillis();
        ZBrainProperties.Retrieval config = properties.getRetrieval();

        // 1. 两路并行召回（向量 + 全文）
        CompletableFuture<List<RetrievalResult>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorRetriever.retrieve(kbId, vectorQuery, config.getVectorTopK()),
                retrievalExecutor);
        CompletableFuture<List<RetrievalResult>> fulltextFuture = CompletableFuture.supplyAsync(
                () -> fullTextRetriever.retrieve(kbId, textQuery, config.getFulltextTopK()),
                retrievalExecutor);

        // 等待两路召回全部完成
        CompletableFuture.allOf(vectorFuture, fulltextFuture).join();

        List<RetrievalResult> vectorResults = vectorFuture.join();
        List<RetrievalResult> fulltextResults = fulltextFuture.join();

        log.debug("两路并行召回完成: vector={}, fulltext={}",
                vectorResults.size(), fulltextResults.size());

        // 2. RRF 融合
        List<List<RetrievalResult>> allResults = List.of(vectorResults, fulltextResults);
        List<RetrievalResult> fused = rrfFusion.fuse(allResults, config.getRrfK(), 10);

        if (fused.isEmpty()) {
            log.warn("RRF 融合后无结果: kbId={}", kbId);
            lastInfo.set(new RetrievalInfo(
                    vectorResults.size(), fulltextResults.size(),
                    0, 0, false, new ArrayList<>()));
            return new ArrayList<>();
        }

        // 3. Rerank 精排
        List<RetrievalResult> finalResults = rerank(fused, textQuery, config.getRerankTopN());

        // 4. 填充父块内容（用于上下文组装）
        fillParentContent(finalResults);

        // 5. 记录检索过程信息
        List<Long> hitChunkIds = finalResults.stream()
                .map(RetrievalResult::getChunkId)
                .collect(Collectors.toList());
        lastInfo.set(new RetrievalInfo(
                vectorResults.size(), fulltextResults.size(),
                fused.size(), finalResults.size(), true, hitChunkIds));

        log.info("混合检索完成: kbId={}, 耗时={}ms, 最终命中={}",
                kbId, System.currentTimeMillis() - start, finalResults.size());
        return finalResults;
    }

    /**
     * Rerank 精排（含降级策略）
     */
    private List<RetrievalResult> rerank(List<RetrievalResult> fused, String query, int topN) {
        try {
            List<String> documents = fused.stream()
                    .map(RetrievalResult::getContent)
                    .toList();

            log.info("Rerank 开始: 候选数={}, topN={}", documents.size(), topN);
            List<RerankService.RerankResult> rerankResults =
                    rerankService.rerank(query, documents, topN);

            List<RetrievalResult> result = new ArrayList<>();
            for (int i = 0; i < rerankResults.size(); i++) {
                RerankService.RerankResult rr = rerankResults.get(i);
                RetrievalResult item = fused.get(rr.index());
                item.setRerankRank(i + 1);
                item.setScore(rr.relevanceScore());
                result.add(item);
            }
            log.info("Rerank 完成: 返回 {} 个结果", result.size());
            return result;
        } catch (Exception e) {
            // 降级策略：Rerank 失败则使用 RRF 融合后的排序结果
            log.warn("Rerank 调用失败，降级使用 RRF 融合结果: {}", e.getMessage());
            int limit = Math.min(topN, fused.size());
            return new ArrayList<>(fused.subList(0, limit));
        }
    }

    /**
     * 填充父块内容
     */
    private void fillParentContent(List<RetrievalResult> results) {
        for (RetrievalResult result : results) {
            if (result.getParentId() != null) {
                Chunk parent = chunkMapper.selectParent(result.getParentId());
                if (parent != null) {
                    result.setParentContent(parent.getContent());
                }
            }
        }
    }

    @Override
    public RetrievalInfo getLastRetrievalInfo() {
        RetrievalInfo info = lastInfo.get();
        lastInfo.remove();
        return info;
    }
}
