package cn.kong.zbrain.enums;

/**
 * 系统提示词逻辑键枚举
 *
 * <p>与数据库 {@code sys_prompt.prompt_key} 一一对应。</p>
 *
 * @author zbrain-team
 */
public enum PromptKey {

    /** 查询改写 */
    QUERY_REWRITE("query_rewrite"),
    /** 关键词提取 */
    KEYWORD_EXTRACT("keyword_extract"),
    /** 闲聊提示词 */
    CHITCHAT("chitchat"),
    /** 无结果提示 */
    NO_RESULT("no_result"),
    /** 意图识别 */
    INTENT_CLASSIFY("intent_classify");

    private final String code;

    PromptKey(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
