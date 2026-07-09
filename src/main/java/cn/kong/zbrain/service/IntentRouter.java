package cn.kong.zbrain.service;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.dto.response.IntentResult;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.dto.response.ThinkingStep;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.enums.ChatIntent;
import cn.kong.zbrain.enums.ChatMode;
import cn.kong.zbrain.enums.SseEventType;
import cn.kong.zbrain.enums.ThinkingStepType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 意图路由器（对话入口中枢）
 *
 * <p>统一对话入口，根据用户选择的模式或自动意图识别结果，
 * 将请求路由到对应的 {@link ChatEngine} 实现。</p>
 *
 * <p>路由优先级：</p>
 * <ol>
 *   <li>显式模式：用户在前端选择了非 AUTO 模式 → 直接使用对应引擎</li>
 *   <li>自动模式：调用 {@link IntentService} 进行 LLM 意图分类 → 按结果选择引擎</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
public class IntentRouter {

    private final IntentService intentService;
    private final ChatContextCache chatContextCache;
    private final ChatSessionHelper helper;
    private final Map<ChatIntent, ChatEngine> engineMap;

    /** 历史对话轮数（用于意图识别的上下文感知） */
    private static final int INTENT_HISTORY_ROUNDS = 3;

    public IntentRouter(IntentService intentService,
                        ChatContextCache chatContextCache,
                        ChatSessionHelper helper,
                        List<ChatEngine> engines) {
        this.intentService = intentService;
        this.chatContextCache = chatContextCache;
        this.helper = helper;
        this.engineMap = engines.stream()
                .collect(Collectors.toMap(ChatEngine::supportedIntent, Function.identity()));
        log.info("IntentRouter 初始化完成，已注册引擎: {}", engineMap.keySet());
    }

    /**
     * 流式对话路由
     */
    public void routeStream(ChatRequest request, SseEmitter emitter) {
        // 1. 创建或获取会话
        ChatSession session = helper.getOrCreateSession(request);

        // 2. 确定意图
        IntentResult intentResult = resolveIntent(request, session);

        ChatIntent effectiveIntent = intentResult.getEffectiveIntent();
        log.info("意图路由: query='{}', mode={}, intent={}, confidence={}, reason={}",
                request.getQuery(), request.getMode(), effectiveIntent,
                intentResult.confidence(), intentResult.reason());

        // 3. 选择引擎
        ChatEngine engine = engineMap.get(effectiveIntent);
        if (engine == null) {
            log.warn("未找到 {} 意图对应的引擎，降级为 RAG", effectiveIntent);
            engine = engineMap.get(ChatIntent.RAG);
            if (engine == null) {
                helper.sendSseEvent(emitter, SseEventType.ERROR.getCode(), "无可用对话引擎");
                emitter.complete();
                return;
            }
        }

        // 4. 确保 sessionId 已设置
        request.setSessionId(session.getId());

        // 5. 发送意图识别思考步骤
        helper.sendSseEvent(emitter, SseEventType.THINKING.getCode(), new ThinkingStep(
                ThinkingStepType.INTENT.getCode(), "意图识别",
                effectiveIntent + " · 置信度 " + (int)(intentResult.confidence() * 100) + "%",
                System.currentTimeMillis()));

        // 6. 委派给引擎执行
        engine.chatStream(request, emitter);
    }

    /**
     * 同步对话路由
     */
    public ChatResponse route(ChatRequest request) {
        // 1. 创建或获取会话
        ChatSession session = helper.getOrCreateSession(request);

        // 2. 确定意图
        IntentResult intentResult = resolveIntent(request, session);

        ChatIntent effectiveIntent = intentResult.getEffectiveIntent();
        log.info("意图路由(同步): query='{}', mode={}, intent={}, confidence={}",
                request.getQuery(), request.getMode(), effectiveIntent, intentResult.confidence());

        // 3. 选择引擎
        ChatEngine engine = engineMap.get(effectiveIntent);
        if (engine == null) {
            log.warn("未找到 {} 意图对应的引擎，降级为 RAG", effectiveIntent);
            engine = engineMap.get(ChatIntent.RAG);
        }

        // 4. 确保 sessionId 已设置
        request.setSessionId(session.getId());

        // 5. 委派给引擎执行
        return engine.chat(request);
    }

    /**
     * 解析意图：显式模式优先，自动模式走 LLM 分类
     */
    private IntentResult resolveIntent(ChatRequest request, ChatSession session) {
        ChatMode mode = parseMode(request.getMode());

        if (!mode.isAuto()) {
            // 显式模式：直接使用用户指定的意图
            return new IntentResult(
                    mode.getForcedIntent(), 1.0,
                    "用户显式选择: " + mode.getLabel(), mode.getForcedIntent());
        }

        // 自动模式：LLM 意图识别（传入对话历史实现上下文感知）
        List<ChatContextCache.ChatMessage> history =
                chatContextCache.getRecentMessages(session.getId(), INTENT_HISTORY_ROUNDS);
        return intentService.classify(request.getQuery(), history, request.getKbId());
    }

    private ChatMode parseMode(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) {
            return ChatMode.AUTO;
        }
        try {
            return ChatMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知的对话模式: {}, 默认 AUTO", modeStr);
            return ChatMode.AUTO;
        }
    }
}
