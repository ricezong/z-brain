package cn.kong.eon.agent.orchestrator;

import cn.kong.eon.agent.tool.ToolExecutionContext;
import cn.kong.eon.agent.tool.ToolRegistry;
import cn.kong.eon.config.ConfigService;
import cn.kong.eon.config.PromptKey;
import cn.kong.eon.event.SseEventPusher;
import cn.kong.eon.event.SseEventType;
import cn.kong.eon.llm.ChatClientFactory;
import cn.kong.eon.persistence.entity.ChatSession;
import cn.kong.eon.persistence.mapper.ChatSessionMapper;
import cn.kong.eon.common.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Agent 编排核心（新工程结构）
 *
 * <p>驱动 Agent Loop（ReAct）：ChatClient（主模型 + Advisor 链 + 工具循环）
 * → SSE 流式推送（SseEventPusher 统一封装）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ChatClientFactory chatClientFactory;
    private final ToolRegistry toolRegistry;
    private final ConfigService configService;
    private final SseEventPusher sseEventPusher;
    private final ChatSessionMapper chatSessionMapper;
    @Qualifier("sseStreamExecutor")
    private final Executor sseStreamExecutor;

    private static final long SSE_TIMEOUT = 300_000L;

    /**
     * 流式 Agent 对话
     */
    public SseEmitter stream(AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> log.debug("Agent SSE 连接完成"));
        emitter.onTimeout(() -> {
            log.warn("Agent SSE 连接超时");
            emitter.complete();
        });
        emitter.onError(e -> log.error("Agent SSE 连接异常", e));

        sseStreamExecutor.execute(() -> {
            try {
                // 1. 创建或获取 agent 模式会话
                ChatSession session = getOrCreateAgentSession(request);
                String sessionId = session.getId();

                // 2. 设置工具执行上下文（含 SSE 推送上下文，供 InnerToolCallback 补发 TOOL_CALL 事件）
                ToolExecutionContext.set(sessionId, null);
                ToolExecutionContext.setSseContext(sseEventPusher, emitter);

                // 3. 推送会话事件
                sseEventPusher.push(emitter, SseEventType.SESSION, sessionId);

                // 4. 读取 Agent 系统提示词（从 ConfigService 读取，Caffeine 缓存）
                String systemPrompt = configService.getPrompt(PromptKey.AGENT_SYSTEM.getCode());
                if (systemPrompt == null || systemPrompt.isBlank()) {
                    systemPrompt = "你是灵犀，用户的个人 AI 助手。";
                }

                // 5. Agent Loop
                log.info("[Agent] Loop 启动: session={}, tools={}, query='{}'",
                        sessionId, toolRegistry.toolNames(), truncate(request.getMessage(), 80));

                chatClientFactory.mainClient()
                        .prompt()
                        .system(systemPrompt)
                        .user(request.getMessage())
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                        .toolCallbacks(toolRegistry.all())
                        .stream()
                        .chatResponse()
                        .doOnNext(resp -> {
                            if (resp.getResult() != null && resp.getResult().getOutput() != null) {
                                String text = resp.getResult().getOutput().getText();
                                if (text != null && !text.isEmpty()) {
                                    sseEventPusher.push(emitter, SseEventType.CONTENT, text);
                                }
                            }
                        })
                        .blockLast();

                // 6. 完成
                chatSessionMapper.incrementMessageCount(sessionId);
                Map<String, Object> doneData = new HashMap<>();
                doneData.put("sessionId", sessionId);
                doneData.put("mode", "agent");
                sseEventPusher.push(emitter, SseEventType.DONE, doneData);
                emitter.complete();
                log.info("[Agent] Loop 完成: session={}", sessionId);

            } catch (Exception e) {
                log.error("[Agent] Loop 异常", e);
                sseEventPusher.push(emitter, SseEventType.ERROR, e.getMessage());
                emitter.complete();
            } finally {
                ToolExecutionContext.clear();
            }
        });

        return emitter;
    }

    private ChatSession getOrCreateAgentSession(AgentChatRequest request) {
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            ChatSession existing = chatSessionMapper.selectById(request.getSessionId());
            if (existing != null) {
                return existing;
            }
        }
        ChatSession session = new ChatSession();
        session.setId(CommonUtils.uuid());
        session.setKbId(request.getKbId());
        String msg = request.getMessage();
        session.setTitle(msg != null && msg.length() > 50 ? msg.substring(0, 50) + "..." : msg);
        session.setUserId(request.getUserId() != null && !request.getUserId().isBlank()
                ? request.getUserId() : "anonymous");
        session.setMessageCount(0);
        session.setMode("agent");
        chatSessionMapper.insert(session);
        return session;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
