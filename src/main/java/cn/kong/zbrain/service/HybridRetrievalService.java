package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.response.RetrievalResult;

import java.util.List;

/**
 * 混合检索服务接口
 *
 * <p>组合向量/全文/模糊三路召回 + RRF 融合 + Rerank 精排。</p>
 *
 * @author zbrain-team
 */
public interface HybridRetrievalService {

    /**
     * 执行混合检索
     *
     * @param kbId         知识库 ID
     * @param vectorQuery  向量检索文本（HyDE 答案或改写后的 Query）
     * @param textQuery    全文/模糊检索文本（改写后的 Query）
     * @return 检索结果（含 Rerank 后的 Top N）
     */
    List<RetrievalResult> hybridRetrieve(Long kbId, String vectorQuery, String textQuery);

    /**
     * 检索过程信息（用于日志记录）
     */
    record RetrievalInfo(
            int vectorCount,
            int fulltextCount,
            int fuzzyCount,
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
