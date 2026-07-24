package cn.kong.eon.event;

/**
 * SSE 事件类型枚举（新工程只保留实际发送的事件）
 *
 * @author eon-team
 */
public enum SseEventType {

    /** 会话创建 */
    SESSION("session"),
    /** 流式内容 */
    CONTENT("content"),
    /** 工具调用中（Agent 体验关键事件） */
    TOOL_CALL("tool_call"),
    /** 引用来源 */
    CITATIONS("citations"),
    /** 上下文压缩通知 */
    COMPRESSION("compression"),
    /** 完成 */
    DONE("done"),
    /** 错误 */
    ERROR("error");

    private final String code;

    SseEventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
