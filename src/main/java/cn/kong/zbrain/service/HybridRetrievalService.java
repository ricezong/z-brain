package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.dto.response.ThinkingStep;

import java.util.List;
import java.util.function.Consumer;

/**
 * 混合检索服务接口
 *
 * <p>组合向量/全文两路并行召回 + RRF 融合 + Rerank 精排。</p>
 *
 * @author zbrain-team
 */
public interface HybridRetrievalService {

    /**
     * 执行混合检索
     *
     * @param kbId         知识库 ID
     * @param vectorQuery  向量检索文本（改写后的 Query）
     * @param textQuery    全文检索文本（改写后的 Query）
     * @return 检索结果（含 Rerank 后的 Top N）
     */
    List<RetrievalResult> hybridRetrieve(Long kbId, String vectorQuery, String textQuery);

    /**
     * 执行混合检索（带思考过程回调）
     *
     * @param kbId         知识库 ID
     * @param vectorQuery  向量检索文本（改写后的 Query）
     * @param textQuery    全文检索文本（改写后的 Query）
     * @param onThinking   思考过程回调（可为 null）
     * @return 检索结果（含 Rerank 后的 Top N）
     */
    List<RetrievalResult> hybridRetrieve(Long kbId, String vectorQuery, String textQuery,
                                          Consumer<ThinkingStep> onThinking);

    /**
     * 检索过程信息（用于日志记录）
     */
    record RetrievalInfo(
            int vectorCount,
            int fulltextCount,
            int fusedCount,
            int rerankCount,
            boolean rerankSuccess,
            List<Long> hitChunkIds
    ) {}

    /**
     * 获取最近一次检索的过程信息
     */
    RetrievalInfo getLastRetrievalInfo();
}
