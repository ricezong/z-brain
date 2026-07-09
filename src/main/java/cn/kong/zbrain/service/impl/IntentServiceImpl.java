package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.dto.response.IntentResult;
import cn.kong.zbrain.enums.ChatIntent;
import cn.kong.zbrain.enums.PromptKey;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.IntentService;
import cn.kong.zbrain.service.SysPromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 意图识别服务实现
 *
 * <p>双层策略：</p>
 * <ol>
 *   <li>规则预筛：明确闲聊关键词直接命中，避免 LLM 调用开销</li>
 *   <li>LLM 结构化分类：将 query + 对话历史 + 知识库信息交给 LLM，返回结构化 JSON</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentServiceImpl implements IntentService {

    private final LLMService llmService;
    private final SysPromptService sysPromptService;
    private final ObjectMapper objectMapper;

    /** 明确闲聊关键词（完全匹配，置信度最高） */
    private static final Pattern CHITCHAT_PATTERN = Pattern.compile(
            "^(你好|您好|hi|hello|hey|嗨|在吗|在不在|谢谢|感谢|再见|拜拜|bye|goodbye|"
                    + "你是谁|你叫什么|你能做什么|帮我什么|介绍下你自己|讲个笑话|"
                    + "无聊|陪我聊天|聊聊天)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public IntentResult classify(String query, List<ChatContextCache.ChatMessage> history, Long kbId) {
        if (query == null || query.isBlank()) {
            return new IntentResult(ChatIntent.RAG, 1.0, "空查询默认RAG", ChatIntent.RAG);
        }

        String trimmed = query.trim();

        // 第一层：规则快速预筛（命中则直接返回，避免 LLM 调用开销）
        if (CHITCHAT_PATTERN.matcher(trimmed).matches()) {
            return new IntentResult(ChatIntent.CHITCHAT, 0.95, "规则匹配：明确闲聊关键词", ChatIntent.RAG);
        }

        // 第二层：LLM 意图分类
        try {
            String promptTemplate = sysPromptService.getContent(PromptKey.INTENT_CLASSIFY.getCode());
            if (promptTemplate == null) {
                log.warn("intent_classify 提示词未配置，默认 RAG");
                return new IntentResult(ChatIntent.RAG, 0.5, "提示词未配置", ChatIntent.RAG);
            }

            String historyText = formatHistory(history);
            String prompt = promptTemplate
                    .replace("{query}", trimmed)
                    .replace("{history}", historyText)
                    .replace("{has_kb}", kbId != null ? "是" : "否");

            String response = llmService.simpleChat(prompt);
            return parseIntentResponse(response);
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，降级为 RAG: {}", e.getMessage());
            return new IntentResult(ChatIntent.RAG, 0.5, "LLM异常降级", ChatIntent.RAG);
        }
    }

    private String formatHistory(List<ChatContextCache.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatContextCache.ChatMessage msg : history) {
            sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    private IntentResult parseIntentResponse(String response) {
        try {
            // LLM 返回 JSON: {"intent": "chitchat", "confidence": 0.9, "reason": "..."}
            var json = objectMapper.readTree(response);
            String intentStr = json.get("intent").asText();
            double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 0.8;
            String reason = json.has("reason") ? json.get("reason").asText() : "";

            ChatIntent intent;
            try {
                intent = ChatIntent.valueOf(intentStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("未知的意图类型: {}, 降级为 RAG", intentStr);
                return new IntentResult(ChatIntent.RAG, 0.5, "未知意图降级: " + intentStr, ChatIntent.RAG);
            }

            ChatIntent fallback = intent == ChatIntent.CHITCHAT ? ChatIntent.RAG : ChatIntent.CHITCHAT;
            return new IntentResult(intent, confidence, reason, fallback);
        } catch (Exception e) {
            log.warn("意图识别响应解析失败: {}, 降级为 RAG", response);
            return new IntentResult(ChatIntent.RAG, 0.5, "解析失败降级", ChatIntent.RAG);
        }
    }
}
