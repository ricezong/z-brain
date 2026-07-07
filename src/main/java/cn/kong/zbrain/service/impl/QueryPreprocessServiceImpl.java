package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.QueryPreprocessService;
import cn.kong.zbrain.service.SysPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 查询预处理服务实现
 *
 * <p>实现意图识别、Query 改写、HyDE 三大能力。
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
    public String generateHyDE(String query) {
        try {
            // 从数据库读取提示词模板
            String promptTemplate = sysPromptService.getContent("hyde");
            if (promptTemplate == null) {
                log.warn("hyde 提示词未配置，使用原始查询");
                return query;
            }

            String prompt = promptTemplate.replace("{query}", query);

            String hyde = llmService.simpleChat(prompt);
            return hyde == null ? query : hyde.trim();
        } catch (Exception e) {
            log.warn("HyDE 生成失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }

    @Override
    public PreprocessResult preprocess(String query, String sessionId) {
        boolean chitchat = isChitchat(query);
        List<ChatContextCache.ChatMessage> history = chatContextCache.getRecentMessages(sessionId, HISTORY_ROUNDS);

        ZBrainProperties.QueryPreprocess qpConfig = properties.getQueryPreprocess();

        String rewrittenQuery = query;
        if (qpConfig.isEnableQueryRewrite() && !chitchat) {
            rewrittenQuery = rewriteQuery(query, sessionId);
        }

        String hydeAnswer = null;
        String vectorQuery = rewrittenQuery;
        if (qpConfig.isEnableHyde() && !chitchat) {
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
