package cn.kong.zbrain.enums;

/**
 * 对话意图枚举
 *
 * <p>每新增一种意图，只需新增一个枚举值并实现对应的 {@code ChatEngine}。</p>
 *
 * @author zbrain-team
 */
public enum ChatIntent {

    CHITCHAT("闲聊", "日常问候、闲聊、身份询问等非知识性问题"),
    RAG("知识库问答", "需要检索知识库回答的专业问题"),
    SEARCH("联网搜索", "需要实时信息的查询（预留）"),
    TOOL("工具调用", "需要调用外部工具完成（预留）");

    private final String label;
    private final String description;

    ChatIntent(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
