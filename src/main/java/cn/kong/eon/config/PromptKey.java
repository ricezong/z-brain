package cn.kong.eon.config;

/**
 * 系统提示词逻辑键枚举（迁移至 config 域）
 *
 * <p>与数据库 sys_prompt.prompt_key 一一对应。</p>
 *
 * @author eon-team
 */
public enum PromptKey {

    QUERY_REWRITE("query_rewrite"),
    KEYWORD_EXTRACT("keyword_extract"),
    NO_RESULT("no_result"),
    AGENT_SYSTEM("agent_system"),
    COMPACT_SUMMARY("compact_summary"),
    MEMORY_REVIEW("memory_review"),
    SKILL_REVIEW("skill_review"),
    OBSERVATION_WRAP("observation_wrap");

    private final String code;

    PromptKey(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
