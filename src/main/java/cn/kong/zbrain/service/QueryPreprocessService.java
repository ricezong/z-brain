package cn.kong.zbrain.service;

import cn.kong.zbrain.cache.ChatContextCache;

import java.util.List;

/**
 * 查询预处理服务接口
 *
 * <p>包含三大能力：</p>
 * <ol>
 *   <li>意图识别路由：判断闲聊或知识问答</li>
 *   <li>多轮对话 Query 改写：将指代不清的 Query 改写为独立完整 Query</li>
 *   <li>HyDE 增强：生成假设性答案用于检索</li>
 * </ol>
 *
 * <p>HyDE 与 Query 改写均为系统级优化，由配置文件统一控制，不对用户暴露开关。</p>
 *
 * @author zbrain-team
 */
public interface QueryPreprocessService {

    /**
     * 意图识别
     *
     * @param query 用户问题
     * @return true 表示闲聊，false 表示知识问答
     */
    boolean isChitchat(String query);

    /**
     * 多轮对话 Query 改写
     *
     * @param query     当前问题
     * @param sessionId 会话 ID
     * @return 改写后的独立完整 Query
     */
    String rewriteQuery(String query, String sessionId);

    /**
     * HyDE 假设性答案生成
     *
     * @param query 改写后的 Query
     * @return 假设性答案（用于向量检索）
     */
    String generateHyDE(String query);

    /**
     * 完整的查询预处理（系统级控制 HyDE 与 Query 改写）
     *
     * @param query     原始问题
     * @param sessionId 会话 ID
     * @return 预处理结果
     */
    PreprocessResult preprocess(String query, String sessionId);

    /**
     * 预处理结果
     */
    record PreprocessResult(
            String originalQuery,
            String rewrittenQuery,
            String hydeAnswer,
            boolean isChitchat,
            /** 用于向量检索的文本（HyDE 答案或改写后的 Query） */
            String vectorQuery,
            /** 用于全文检索的文本（改写后的 Query） */
            String textQuery,
            List<ChatContextCache.ChatMessage> history
    ) {}
}
