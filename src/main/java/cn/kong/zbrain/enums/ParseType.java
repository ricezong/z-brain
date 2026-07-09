package cn.kong.zbrain.enums;

/**
 * 文档解析方式枚举
 *
 * @author zbrain-team
 */
public enum ParseType {

    /** Tika 本地解析 */
    TIKA("tika"),
    /** LlamaIndex Cloud 云端解析 */
    LLAMA_INDEX("llama_index");

    private final String code;

    ParseType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
