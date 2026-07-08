package cn.kong.zbrain.enums;

/**
 * 对话模式枚举（用户在前端显式选择）
 *
 * <p>当用户选择了非 AUTO 模式时，跳过自动意图识别，直接路由到对应引擎。</p>
 *
 * @author zbrain-team
 */
public enum ChatMode {

    /** 自动识别意图 */
    AUTO("自动识别", null),
    /** 强制闲聊 */
    CHITCHAT("闲聊", ChatIntent.CHITCHAT),
    /** 强制知识库问答 */
    RAG("知识库问答", ChatIntent.RAG);

    private final String label;
    private final ChatIntent forcedIntent;

    ChatMode(String label, ChatIntent forcedIntent) {
        this.label = label;
        this.forcedIntent = forcedIntent;
    }

    public String getLabel() {
        return label;
    }

    public ChatIntent getForcedIntent() {
        return forcedIntent;
    }

    public boolean isAuto() {
        return this.forcedIntent == null;
    }
}
