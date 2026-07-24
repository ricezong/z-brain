package cn.kong.eon.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 统一 SSE 事件推送抽象
 *
 * <p>封装序列化（Object→JSON）+ 超时 + 容错 + 攒批优化。
 * 替代旧工程散落各处的 sendSseEvent 直调。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class SseEventPusher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 推送单个 SSE 事件
     *
     * @param emitter SSE 发射器
     * @param type    事件类型
     * @param data    事件数据（将序列化为 JSON）
     */
    public void push(SseEmitter emitter, SseEventType type, Object data) {
        try {
            String json = data == null ? "" : MAPPER.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(type.getCode()).data(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 推送跳过(连接关闭): {}", type);
        }
    }

    /**
     * 批量推送 content chunks（攒批减少 SSE 帧数）
     *
     * @param emitter SSE 发射器
     * @param chunks  文本片段列表
     */
    public void pushContentBatch(SseEmitter emitter, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        push(emitter, SseEventType.CONTENT, String.join("", chunks));
    }

    /**
     * 推送工具调用事件
     *
     * @param emitter  SSE 发射器
     * @param toolName 工具名
     * @param reason   调用动机
     * @param argsJson 参数 JSON
     */
    public void pushToolCall(SseEmitter emitter, String toolName, String reason, String argsJson) {
        push(emitter, SseEventType.TOOL_CALL, new ToolCallEvent(toolName, reason, argsJson));
    }

    /**
     * 工具调用事件数据
     */
    public record ToolCallEvent(String toolName, String reason, String argsJson) {}
}
