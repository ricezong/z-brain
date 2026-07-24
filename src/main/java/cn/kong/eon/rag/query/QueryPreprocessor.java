package cn.kong.eon.rag.query;

import cn.kong.eon.config.ConfigService;
import cn.kong.eon.config.PromptKey;
import cn.kong.eon.llm.ChatClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 查询预处理器（重写：去 LLMService/ChatContextCache/SysPromptService 耦合）
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>有对话历史 → LLM 改写（指代消解）</li>
 *   <li>无对话历史 → 关键词扩展</li>
 * </ul>
 *
 * <p>提示词从 ConfigService 读取（Caffeine 缓存），LLM 调用走 lightClient。</p>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPreprocessor {

    private final ChatClientFactory chatClientFactory;
    private final ConfigService configService;

    /**
     * 改写查询
     *
     * @param query     原始查询
     * @param history   对话历史（每行 "role: content"），可为空
     * @return 改写后的查询；失败时返回原始查询
     */
    public String rewrite(String query, String history) {
        if (query == null || query.isBlank()) {
            return query;
        }

        try {
            if (history == null || history.isBlank()) {
                return extractKeywords(query);
            }

            String template = configService.getPrompt(PromptKey.QUERY_REWRITE.getCode());
            if (template == null || template.isBlank()) {
                log.warn("[QueryPreprocessor] query_rewrite 提示词未配置");
                return query;
            }

            String prompt = template
                    .replace("{history}", history)
                    .replace("{query}", query);

            String rewritten = chatClientFactory.lightClient().prompt()
                    .user(prompt)
                    .call()
                    .content();

            return (rewritten != null && !rewritten.isBlank()) ? rewritten.trim() : query;
        } catch (Exception e) {
            log.warn("[QueryPreprocessor] 改写失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }

    /**
     * 关键词扩展（无对话历史时）
     */
    private String extractKeywords(String query) {
        try {
            String template = configService.getPrompt(PromptKey.KEYWORD_EXTRACT.getCode());
            if (template == null || template.isBlank()) {
                return query;
            }
            String prompt = template.replace("{query}", query);
            String expanded = chatClientFactory.lightClient().prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (expanded != null && !expanded.isBlank() && expanded.trim().length() > 3) {
                return expanded.trim();
            }
        } catch (Exception e) {
            log.debug("[QueryPreprocessor] 关键词扩展失败: {}", e.getMessage());
        }
        return query;
    }
}
