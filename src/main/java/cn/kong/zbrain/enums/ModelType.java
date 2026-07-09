package cn.kong.zbrain.enums;

/**
 * LLM 模型类型枚举
 *
 * <p>区分模型用途：chat-对话 / embedding-向量 / rerank-重排。</p>
 *
 * @author zbrain-team
 */
public enum ModelType {

    /** 对话模型 */
    CHAT("chat"),
    /** 向量模型 */
    EMBEDDING("embedding"),
    /** 重排模型 */
    RERANK("rerank");

    private final String code;

    ModelType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ModelType fromCode(String code) {
        for (ModelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的模型类型: " + code);
    }
}
