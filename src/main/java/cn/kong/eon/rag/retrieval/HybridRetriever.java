package cn.kong.eon.rag.retrieval;

import cn.kong.eon.persistence.dto.response.RetrievalResult;

import java.util.List;

/**
 * 混合检索器接口
 *
 * <p>两路并行召回（向量+全文）→ RRF 融合 → Rerank 精排 → citation 编号下沉。</p>
 *
 * @author eon-team
 */
public interface HybridRetriever {

    /**
     * 执行混合检索
     *
     * @param kbId       知识库 ID（null 全局检索）
     * @param vectorQuery 向量检索查询（改写后的完整问句）
     * @param textQuery   全文检索查询（关键词扩展后的查询）
     * @return 检索结果列表（已 rerank + citation 编号 + parentContent 回填）
     */
    List<RetrievalResult> retrieve(Long kbId, String vectorQuery, String textQuery);
}
