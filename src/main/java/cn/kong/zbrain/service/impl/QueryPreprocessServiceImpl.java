package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.QueryPreprocessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 查询预处理服务实现
 *
 * <p>实现意图识别、Query 改写、HyDE 三大能力。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPreprocessServiceImpl implements QueryPreprocessService {

    private final LLMService llmService;
    private final ChatContextCache chatContextCache;

    /** 闲聊关键词模式（必须完全匹配才判定为闲聊） */
    private static final Pattern CHITCHAT_PATTERN = Pattern.compile(
            "^(你好|您好|hi|hello|hey|嗨|在吗|在不在|谢谢|感谢|再见|拜拜|bye|goodbye|"
                    + "你是谁|你叫什么|你能做什么|帮我什么|介绍下你自己|讲个笑话|"
                    + "无聊|陪我聊天|聊聊天)$",
            Pattern.CASE_INSENSITIVE
    );

    /** 闲聊最大长度阈值 */
    private static final int CHITCHAT_MAX_LENGTH = 6;

    /** 历史对话轮数 */
    private static final int HISTORY_ROUNDS = 3;

    @Override
    public boolean isChitchat(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String trimmed = query.trim();
        // 1. 关键词匹配
        if (CHITCHAT_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        // 2. 短文本 + 无问号 + 无疑问词 -> 可能是闲聊
        if (trimmed.length() <= CHITCHAT_MAX_LENGTH
                && !trimmed.contains("?")
                && !trimmed.contains("？")
                && !trimmed.contains("什么")
                && !trimmed.contains("怎么")
                && !trimmed.contains("如何")
                && !trimmed.contains("为什么")
                && !trimmed.contains("哪里")
                && !trimmed.contains("哪个")) {
            return true;
        }
        return false;
    }

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

            String prompt = """
                    你是一个查询改写助手。根据以下多轮对话历史，将用户的最新问题改写为一个独立、完整、清晰的查询。
                    要求：
                    1. 解决指代不清的问题（如"它"、"这个"、"那个"等代词替换为具体对象）
                    2. 保持原意，不要扩展或改变用户意图
                    3. 补充必要的上下文信息，使查询自包含
                    4. 直接输出改写后的查询，不要有任何解释或前缀

                    对话历史：
                    %s

                    最新问题：%s

                    改写后的查询：
                    """.formatted(historyText, query);

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
            String prompt = """
                    请从以下查询中提取核心关键词，并扩展为更适合语义检索的形式。
                    要求：
                    1. 去除"的"、"了"、"是"等无意义停用词
                    2. 补充相关同义词或上下位词
                    3. 保持查询的核心语义不变
                    4. 输出扩展后的查询文本，不超过 50 字，不要有任何解释或前缀

                    原始查询：%s

                    扩展后的查询：
                    """.formatted(query);

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
    public String generateHyDE(String query) {
        try {
            String prompt = """
                    请针对以下问题，生成一段简短的假设性答案（100-200字）。
                    这段答案将用于向量检索，所以请尽可能包含与问题相关的关键词和概念。
                    直接输出答案内容，不要有任何解释或前缀。

                    问题：%s

                    假设性答案：
                    """.formatted(query);

            String hyde = llmService.simpleChat(prompt);
            return hyde == null ? query : hyde.trim();
        } catch (Exception e) {
            log.warn("HyDE 生成失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }

    @Override
    public PreprocessResult preprocess(String query, String sessionId,
                                       boolean enableQueryRewrite, boolean enableHyde) {
        boolean chitchat = isChitchat(query);
        List<ChatContextCache.ChatMessage> history = chatContextCache.getRecentMessages(sessionId, HISTORY_ROUNDS);

        String rewrittenQuery = query;
        if (enableQueryRewrite && !chitchat) {
            rewrittenQuery = rewriteQuery(query, sessionId);
        }

        String hydeAnswer = null;
        String vectorQuery = rewrittenQuery;
        if (enableHyde && !chitchat) {
            hydeAnswer = generateHyDE(rewrittenQuery);
            // 确保 vectorQuery 不为 null
            if (hydeAnswer != null && !hydeAnswer.isBlank()) {
                vectorQuery = hydeAnswer;
            } else {
                log.warn("HyDE 生成为空，使用改写后的查询作为向量查询");
                hydeAnswer = null;
            }
        }

        return new PreprocessResult(
                query,
                rewrittenQuery,
                hydeAnswer,
                chitchat,
                vectorQuery,
                rewrittenQuery,
                history
        );
    }
}
