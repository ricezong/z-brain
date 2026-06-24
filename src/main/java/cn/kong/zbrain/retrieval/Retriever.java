package cn.kong.zbrain.retrieval;

import cn.kong.zbrain.dto.response.RetrievalResult;

import java.util.List;

/**
 * 检索器接口
 *
 * @author zbrain-team
 */
public interface Retriever {

    /**
     * 执行检索
     *
     * @param kbId  知识库 ID
     * @param query 查询文本
     * @param topK  召回数量
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieve(Long kbId, String query, int topK);

    /**
     * 检索器名称（用于 RRF 融合时标识来源）
     */
    String name();
}
