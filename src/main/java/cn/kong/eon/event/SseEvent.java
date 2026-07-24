package cn.kong.eon.event;

/**
 * SSE 事件对象
 *
 * @param type      事件类型
 * @param data      事件数据
 * @param timestamp 时间戳
 * @author eon-team
 */
public record SseEvent(String type, Object data, long timestamp) {
}
