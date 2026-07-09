package cn.kong.zbrain.enums;

/**
 * 对话消息角色枚举
 *
 * @author zbrain-team
 */
public enum ChatRole {

    /** 用户消息 */
    USER("user"),
    /** 助手消息 */
    ASSISTANT("assistant"),
    /** 系统消息 */
    SYSTEM("system");

    private final String code;

    ChatRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
