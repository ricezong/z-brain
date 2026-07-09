package cn.kong.zbrain.service;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.dto.response.ThinkingStep;

import java.util.List;
import java.util.function.Consumer;

/**
 * 查询预处理服务接口
 *
 * <p>包含能力：</p>
 * <ol>
 *   <li>多轮对话 Query 改写：将指代不清的 Query 改写为独立完整 Query</li>
 * </ol>
 *
 * <p>意图识别已迁移至 {@link cn.kong.zbrain.service.IntentService}。
 * Query 改写为系统级优化，由配置文件统一控制，不对用户暴露开关。</p>
 *
 * @author zbrain-team
 */
public interface QueryPreprocessService {

    /**
     * 多轮对话 Query 改写
     *
     * @param query     当前问题
     * @param sessionId 会话 ID
     * @return 改写后的独立完整 Query
     */
    String rewriteQuery(String query, String sessionId);

    /**
     * 用户主动触发的提示词优化（API 接口专用）
     *
     * <p>无论是否有对话历史，都使用 LLM 进行完整改写，
     * 使查询更清晰、更完整、更适合检索。</p>
     *
     * @param query     当前问题
     * @param sessionId 会话 ID（可选）
     * @return 优化后的 Query
     */
    String rewriteQueryForApi(String query, String sessionId);

    /**
     * 完整的查询预处理（系统级控制 Query 改写）
     *
     * @param query     原始问题
     * @param sessionId 会话 ID
     * @return 预处理结果
     */
    PreprocessResult preprocess(String query, String sessionId);

    /**
     * 完整的查询预处理（带思考过程回调）
     *
     * @param query     原始问题
     * @param sessionId 会话 ID
     * @param onThinking 思考过程回调（可为 null）
     * @return 预处理结果
     */
    PreprocessResult preprocess(String query, String sessionId,
                                 Consumer<ThinkingStep> onThinking);

    /**
     * 预处理结果
     */
    record PreprocessResult(
            String originalQuery,
            String rewrittenQuery,
            boolean isChitchat,
            /** 用于向量检索的文本（改写后的 Query） */
            String vectorQuery,
            /** 用于全文检索的文本（改写后的 Query） */
            String textQuery,
            List<ChatContextCache.ChatMessage> history
    ) {}
}
