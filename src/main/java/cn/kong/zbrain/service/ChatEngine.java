package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.enums.ChatIntent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话引擎接口（策略模式）
 *
 * <p>不同意图对应不同的引擎实现，引擎之间完全解耦。
 * 新增意图只需：1) 在 {@link ChatIntent} 中新增枚举值；
 * 2) 新增一个实现本接口的 {@code @Service} 类。</p>
 *
 * @author zbrain-team
 */
public interface ChatEngine {

    /**
     * 引擎支持的意图
     */
    ChatIntent supportedIntent();

    /**
     * 同步对话
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式对话（SSE）
     *
     * @param request 对话请求（sessionId 已由路由器填充）
     * @param emitter SSE 发射器
     */
    void chatStream(ChatRequest request, SseEmitter emitter);
}
