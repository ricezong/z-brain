package cn.kong.zbrain.dto.response;

/**
 * 思考过程步骤
 *
 * <p>用于在 SSE 流式对话中向前端逐步推送 RAG 管线的执行进度，
 * 前端以时间线形式展示，让用户感知到"思考过程"。</p>
 *
 * @param step      步骤标识：intent / query_rewrite / vector_retrieval / fulltext_retrieval / rrf_fusion / rerank / retrieval_complete / generation
 * @param title     显示标题：意图识别 / 查询改写 / 向量检索 / ...
 * @param detail    详情文本：命中 20 条 / 耗时 1309ms · 最终命中 5 条
 * @param timestamp 毫秒时间戳
 *
 * @author zbrain-team
 */
public record ThinkingStep(
        String step,
        String title,
        String detail,
        long timestamp
) {}
