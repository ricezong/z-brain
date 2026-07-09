package cn.kong.zbrain.enums;

/**
 * SSE 事件类型枚举
 *
 * <p>定义前后端 SSE 通信的事件名称协议。</p>
 *
 * @author zbrain-team
 */
public enum SseEventType {

    /** 会话创建 */
    SESSION("session"),
    /** 意图识别结果 */
    INTENT("intent"),
    /** 思考过程步骤 */
    THINKING("thinking"),
    /** 引用来源 */
    CITATIONS("citations"),
    /** 流式内容 */
    CONTENT("content"),
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
