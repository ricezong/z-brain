package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.response.ThinkingStep;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.QueryPreprocessService;
import cn.kong.zbrain.service.SysPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 查询预处理服务实现
 *
 * <p>实现 Query 改写能力。
 * 意图识别已迁移至 {@link cn.kong.zbrain.service.impl.IntentServiceImpl}。
 * 所有提示词从数据库 sys_prompt 表读取，不再硬编码。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPreprocessServiceImpl implements QueryPreprocessService {

    private final LLMService llmService;
    private final ChatContextCache chatContextCache;
    private final ZBrainProperties properties;
    private final SysPromptService sysPromptService;

    /** 历史对话轮数 */
    private static final int HISTORY_ROUNDS = 3;

    @Override
    public String rewriteQuery(String query, String sessionId) {
        List<ChatContextCache.ChatMessage> history = chatContextCache.getRecentMessages(sessionId, HISTORY_ROUNDS);

        // 无历史对话：做关键词提取增强，不做 LLM 改写
        if (history.isEmpty()) {
            return extractKeywords(query);
        }

        try {
            StringBuilder historyText = new StringBuilder();
            for (ChatContextCache.ChatMessage msg : history) {
                historyText.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }

            // 从数据库读取提示词模板
            String promptTemplate = sysPromptService.getContent("query_rewrite");
            if (promptTemplate == null) {
                log.warn("query_rewrite 提示词未配置，使用原始查询");
                return query;
            }

            String prompt = promptTemplate
                    .replace("{history}", historyText.toString())
                    .replace("{query}", query);

            String rewritten = llmService.simpleChat(prompt);
            return rewritten == null ? query : rewritten.trim();
        } catch (Exception e) {
            log.warn("Query 改写失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }

    /**
     * 关键词提取增强（单轮对话时使用）
     *
     * <p>将查询扩展为更适合向量检索的形式：
     * 去除停用词，保留核心关键词，并补充同义词。</p>
     */
    private String extractKeywords(String query) {
        try {
            // 从数据库读取提示词模板
            String promptTemplate = sysPromptService.getContent("keyword_extract");
            if (promptTemplate == null) {
                log.warn("keyword_extract 提示词未配置，使用原始查询");
                return query;
            }

            String prompt = promptTemplate.replace("{query}", query);

            String expanded = llmService.simpleChat(prompt);
            if (expanded != null && !expanded.isBlank() && expanded.trim().length() > 3) {
                log.debug("关键词扩展: {} -> {}", query, expanded.trim());
                return expanded.trim();
            }
        } catch (Exception e) {
            log.warn("关键词扩展失败，使用原始查询: {}", e.getMessage());
        }
        return query;
    }

    @Override
    public String rewriteQueryForApi(String query, String sessionId) {
        // 用户主动触发：无论有无历史都使用 LLM 改写
        try {
            List<ChatContextCache.ChatMessage> history =
                    (sessionId != null && !sessionId.isBlank())
                            ? chatContextCache.getRecentMessages(sessionId, HISTORY_ROUNDS)
                            : List.of();

            StringBuilder historyText = new StringBuilder();
            for (ChatContextCache.ChatMessage msg : history) {
                historyText.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }

            String promptTemplate = sysPromptService.getContent("query_rewrite");
            if (promptTemplate == null) {
                log.warn("query_rewrite 提示词未配置，使用原始查询");
                return query;
            }

            String prompt = promptTemplate
                    .replace("{history}", historyText.toString())
                    .replace("{query}", query);

            String rewritten = llmService.simpleChat(prompt);
            if (rewritten == null || rewritten.isBlank()) {
                log.warn("LLM 改写返回空，使用原始查询");
                return query;
            }
            return rewritten.trim();
        } catch (Exception e) {
            log.warn("Query 改写失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }

    @Override
    public PreprocessResult preprocess(String query, String sessionId) {
        return preprocess(query, sessionId, null);
    }

    @Override
    public PreprocessResult preprocess(String query, String sessionId,
                                        Consumer<ThinkingStep> onThinking) {
        // 意图识别已由 IntentService 负责，此处始终执行完整的 RAG 预处理
        List<ChatContextCache.ChatMessage> history = chatContextCache.getRecentMessages(sessionId, HISTORY_ROUNDS);

        ZBrainProperties.QueryPreprocess qpConfig = properties.getQueryPreprocess();

        String rewrittenQuery = query;
        if (qpConfig.isEnableQueryRewrite()) {
            rewrittenQuery = rewriteQuery(query, sessionId);
        }

        if (onThinking != null) {
            onThinking.accept(new ThinkingStep(
                    "query_rewrite", "查询改写",
                    rewrittenQuery, System.currentTimeMillis()));
        }

        return new PreprocessResult(
                query,
                rewrittenQuery,
                false,
                rewrittenQuery,
                rewrittenQuery,
                history
        );
    }
}
