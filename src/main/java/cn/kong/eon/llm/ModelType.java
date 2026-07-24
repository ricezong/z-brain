package cn.kong.eon.llm;

/**
 * LLM 模型类型枚举（迁移至 llm 域）
 *
 * @author eon-team
 */
public enum ModelType {

    CHAT("chat"),
    CHAT_LIGHT("chat_light"),
    EMBEDDING("embedding"),
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
