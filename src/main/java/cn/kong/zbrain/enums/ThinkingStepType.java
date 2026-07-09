package cn.kong.zbrain.enums;

/**
 * 思考过程步骤类型枚举
 *
 * <p>用于 SSE thinking 事件中标识当前步骤类型。</p>
 *
 * @author zbrain-team
 */
public enum ThinkingStepType {

    /** 意图识别 */
    INTENT("intent"),
    /** 查询改写 */
    QUERY_REWRITE("query_rewrite"),
    /** 向量检索 */
    VECTOR_RETRIEVAL("vector_retrieval"),
    /** 全文检索 */
    FULLTEXT_RETRIEVAL("fulltext_retrieval"),
    /** RRF 融合 */
    RRF_FUSION("rrf_fusion"),
    /** 重排序 */
    RERANK("rerank"),
    /** 检索完成 */
    RETRIEVAL_COMPLETE("retrieval_complete"),
    /** 生成回答 */
    GENERATION("generation");

    private final String code;

    ThinkingStepType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
