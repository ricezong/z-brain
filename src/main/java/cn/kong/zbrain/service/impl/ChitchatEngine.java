package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.enums.ChatIntent;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.ChatEngine;
import cn.kong.zbrain.service.ChatSessionHelper;
import cn.kong.zbrain.service.SysPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 闲聊引擎
 *
 * <p>不经过 RAG 检索，直接调用 LLM 进行对话。
 * 适用于问候、感谢、身份询问、日常聊天等非知识性问题。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChitchatEngine implements ChatEngine {

    private final LLMService llmService;
    private final SysPromptService sysPromptService;
    private final ChatContextCache chatContextCache;
    private final ChatSessionHelper helper;

    /** 历史对话轮数 */
    private static final int HISTORY_ROUNDS = 3;

    @Override
    public ChatIntent supportedIntent() {
        return ChatIntent.CHITCHAT;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        ChatSession session = helper.getOrCreateSession(request);
        List<ChatContextCache.ChatMessage> history =
                chatContextCache.getRecentMessages(session.getId(), HISTORY_ROUNDS);

        String chitchatPrompt = getChitchatPrompt();
        String answer = llmService.chat(request.getModelId(), chitchatPrompt, request.getQuery(),
                helper.convertHistory(history), Boolean.TRUE.equals(request.getThinking()));

        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getId());
        response.setQuery(request.getQuery());
        response.setAnswer(answer);
        response.setCitations(new ArrayList<>());
        response.setHitChunkIds(new ArrayList<>());
        response.setCostTimeMs(System.currentTimeMillis() - startTime);
        response.setIntent("chitchat");

        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(answer));
        helper.incrementMessageCount(session.getId());
        helper.saveLog(request, session, null, response, new ArrayList<>());
        return response;
    }

    @Override
    public void chatStream(ChatRequest request, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        try {
            ChatSession session = helper.getOrCreateSession(request);
            List<ChatContextCache.ChatMessage> history =
                    chatContextCache.getRecentMessages(session.getId(), HISTORY_ROUNDS);

            helper.sendSseEvent(emitter, "session", session.getId());
            helper.sendSseEvent(emitter, "intent", "chitchat");

            String chitchatPrompt = getChitchatPrompt();
            StringBuilder fullAnswer = new StringBuilder();
            AtomicReference<Usage> usageRef = new AtomicReference<>();
            llmService.chatStream(
                    request.getModelId(),
                    chitchatPrompt,
                    request.getQuery(),
                    helper.convertHistory(history),
                    Boolean.TRUE.equals(request.getThinking()),
                    chunk -> {
                        fullAnswer.append(chunk);
                        helper.sendSseEvent(emitter, "content", chunk);
                    },
                    usage -> usageRef.set(usage)
            );

            // 上下文与日志沉淀
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(fullAnswer.toString()));
            helper.incrementMessageCount(session.getId());

            // 保存日志到数据库（供历史消息加载）
            ChatResponse logResponse = new ChatResponse();
            logResponse.setSessionId(session.getId());
            logResponse.setQuery(request.getQuery());
            logResponse.setAnswer(fullAnswer.toString());
            logResponse.setCitations(new ArrayList<>());
            logResponse.setHitChunkIds(new ArrayList<>());
            logResponse.setCostTimeMs(System.currentTimeMillis() - startTime);
            logResponse.setIntent("chitchat");
            Usage usage = usageRef.get();
            if (usage != null) {
                ChatResponse.TokenMeta tu = new ChatResponse.TokenMeta();
                tu.setPromptTokens(usage.getPromptTokens());
                tu.setCompletionTokens(usage.getCompletionTokens());
                tu.setTotalTokens(usage.getTotalTokens());
                logResponse.setTokenMeta(tu);
            }
            helper.saveLog(request, session, null, logResponse, new ArrayList<>());

            helper.sendSseEvent(emitter, "done", buildDoneData(startTime, usageRef.get()));
            emitter.complete();

        } catch (Exception e) {
            log.error("闲聊引擎流式失败", e);
            helper.sendSseEvent(emitter, "error", e.getMessage());
            emitter.complete();
        }
    }

    private String getChitchatPrompt() {
        String chitchatPrompt = sysPromptService.getContent("chitchat");
        if (chitchatPrompt == null) {
            chitchatPrompt = "你是智多星知识库助手，请友好地回答用户问题。回答使用 Markdown 格式输出。";
        }
        return chitchatPrompt;
    }

    /** 构建 done 事件数据（含耗时、意图、Token 用量） */
    private static Map<String, Object> buildDoneData(long startTime, Usage usage) {
        Map<String, Object> doneData = new HashMap<>();
        doneData.put("costTimeMs", System.currentTimeMillis() - startTime);
        doneData.put("intent", "chitchat");
        if (usage != null) {
            doneData.put("promptTokens", usage.getPromptTokens());
            doneData.put("completionTokens", usage.getCompletionTokens());
            doneData.put("totalTokens", usage.getTotalTokens());
        }
        return doneData;
    }
}
