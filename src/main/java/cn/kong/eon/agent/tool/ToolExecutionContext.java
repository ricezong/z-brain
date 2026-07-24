package cn.kong.eon.agent.tool;

import cn.kong.eon.event.SseEventPusher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工具执行上下文（请求级）
 *
 * <p>Agent Loop 运行在独立虚拟线程中，请求开始时由编排层设置，
 * 结束后清除。附带结果处理器获取 sessionId（artifact 外置目录录）、
 * taskId（任务关联）等请求级信息。</p>
 *
 * <p>SSE 推送集成：携带 SseEventPusher + SseEmitter，
 * 供 InnerToolCallback 在工具调用时补发 TOOL_CALL 事件（设计文档 §4.3）。</p>
 *
 * @author eon-team
 */
public final class ToolExecutionContext {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TASK_ID = new ThreadLocal<>();
    private static final ThreadLocal<SseEventPusher> SSE_PUSHER = new ThreadLocal<>();
    private static final ThreadLocal<SseEmitter> SSE_EMITTER = new ThreadLocal<>();

    private ToolExecutionContext() {
    }

    public static void set(String sessionId, String taskId) {
        SESSION_ID.set(sessionId);
        TASK_ID.set(taskId);
    }

    /**
     * 设置 SSE 推送上下文（AgentOrchestrator 在 Agent Loop 启动时调用）
     */
    public static void setSseContext(SseEventPusher pusher, SseEmitter emitter) {
        SSE_PUSHER.set(pusher);
        SSE_EMITTER.set(emitter);
    }

    public static String sessionId() {
        String sid = SESSION_ID.get();
        return sid != null ? sid : "unknown-session";
    }

    public static String taskId() {
        return TASK_ID.get();
    }

    /**
     * 推送 TOOL_CALL 事件（供 InnerToolCallback 调用，补发工具调用可视化事件）
     */
    public static void pushToolCall(String toolName, String reason, String argsJson) {
        SseEventPusher pusher = SSE_PUSHER.get();
        SseEmitter emitter = SSE_EMITTER.get();
        if (pusher != null && emitter != null) {
            pusher.pushToolCall(emitter, toolName, reason, argsJson);
        }
    }

    public static void clear() {
        SESSION_ID.remove();
        TASK_ID.remove();
        SSE_PUSHER.remove();
        SSE_EMITTER.remove();
    }
}
